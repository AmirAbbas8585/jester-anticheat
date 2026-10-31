package ac.jester.anticheat.utils.data.interpolation.stepped;

import ac.jester.anticheat.utils.collisions.datatypes.SimpleCollisionBox;
import ac.jester.anticheat.utils.data.interpolation.EntityInterpolation;
import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import com.github.retrooper.packetevents.protocol.vector.positionpath.SteppedPositionPath;
import com.github.retrooper.packetevents.util.Vector3d;

public final class SteppedEntityInterpolation implements EntityInterpolation {
    private static final double SNAP_DISTANCE_SQUARED = 4096;

    private final int interpolationInterval;
    private final SteppedInterpolationRange progressRange = new SteppedInterpolationRange();

    private SteppedInterpolationState state;
    private SimpleCollisionBox positionBounds;
    private SimpleCollisionBox segmentStartBounds;

    private double retiredDuration;
    private double confirmedDuration;

    private double upperBoundSkippedThrough;
    private int acknowledgedTransaction = Integer.MIN_VALUE;
    private boolean hasUncertainty;

    public SteppedEntityInterpolation(int interpolationInterval, SimpleCollisionBox position) {
        if (interpolationInterval <= 0) {
            throw new IllegalArgumentException("Interpolation interval must be positive");
        }

        this.interpolationInterval = interpolationInterval;
        reset(position);
    }

    @Override
    public void initializeRotation(float yaw, float pitch) {
        state.yaw = yaw;
        state.pitch = pitch;
    }

    @Override
    public void begin(PositionPath path, boolean relative, boolean hasPos, double packetX, double packetY, double packetZ, Float yaw, Float pitch, boolean positionSync, int transaction) {
        acknowledgedTransaction = Math.min(acknowledgedTransaction, transaction - 1);

        boolean hasPendingSteps = state.hasSteps();
        Vector3d targetPosition = state.resolveTargetPosition(path);
        float targetYaw = state.resolveTargetYaw(yaw);
        float targetPitch = state.resolveTargetPitch(pitch);

        if (!hasPendingSteps) {
            SteppedInterpolationUtils.copyBounds(positionBounds, segmentStartBounds);
        }

        if (positionSync && path != null && applyDistantSnap(targetPosition, targetYaw, targetPitch, transaction)) {
            return;
        }

        boolean sameTargetPosition = hasPendingSteps && targetPosition.equals(state.targetPosition);
        SimpleCollisionBox previousTargetBounds = hasPendingSteps ? lastStep().positionBounds() : positionBounds;

        if (hasUncertainty && path instanceof SteppedPositionPath steppedPath && !SteppedInterpolationUtils.isPoint(previousTargetBounds)) {
            appendPathWithUncertainTarget(steppedPath, previousTargetBounds, targetPosition, targetYaw, targetPitch, transaction);
            return;
        }

        boolean mayIgnoreUpdate = (hasUncertainty && (sameTargetPosition || path == null)) || (sameTargetPosition && state.targetYaw == targetYaw && state.targetPitch == targetPitch);
        if (mayIgnoreUpdate) {
            SimpleCollisionBox targetBounds = path == null ? previousTargetBounds.copy() : SteppedInterpolationUtils.pointBounds(targetPosition);
            appendOptionalStep(targetPosition, targetYaw, targetPitch, interpolationInterval, targetBounds, transaction);
        } else {
            state.appendMovement(path, targetPosition, targetYaw, targetPitch, interpolationInterval, transaction);
        }
    }

    @Override
    public void confirm(int transaction) {
        acknowledgedTransaction = Math.max(acknowledgedTransaction, transaction);
        confirmedDuration = retiredDuration;

        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            SteppedInterpolationState.Step step = state.steps.get(stepIndex);
            if (step.transaction() > acknowledgedTransaction) {
                break;
            }

            if (!hasUncertainty && step.optionalDurationTicks() != 0) {
                state.availableDuration -= step.optionalDurationTicks();

                step = new SteppedInterpolationState.Step(step.position(), step.yaw(), step.pitch(), 0, step.transaction());
                state.steps.set(stepIndex, step);
            }

            confirmedDuration += step.maximumDurationTicks();
        }
    }

    @Override
    public void tick(boolean tickingReliably) {
        if (!state.hasSteps()) {
            return;
        }

        boolean allMovementsDelivered = lastStep().transaction() <= acknowledgedTransaction;
        if (!allMovementsDelivered || !tickingReliably) {
            enableProgressBounds();
        }

        if (!hasUncertainty) {
            tickExactSampler();
            return;
        }

        double optionalDuration = skipOptionalStepsForUpperBound();
        if (!tickingReliably) {
            progressRange.skipTicks(state.availableDuration, retiredDuration, interpolationInterval);
            samplePositionBounds(retiredDuration, state.availableDuration);
            return;
        }

        progressRange.tick(confirmedDuration, state.availableDuration, retiredDuration, interpolationInterval, optionalDuration);

        double minimumProgress = Math.max(retiredDuration, progressRange.sampledMinimumProgress - progressRange.roundingError);
        double maximumProgress = Math.min(state.availableDuration, progressRange.sampledMaximumProgress + progressRange.roundingError);
        samplePositionBounds(minimumProgress, maximumProgress);

        if (allMovementsDelivered && progressRange.sampledMinimumProgress >= state.availableDuration) {
            finishInterpolation();
        } else {
            retireCompletedSteps(minimumProgress);
        }
    }

    @Override
    public void reset(SimpleCollisionBox position) {
        float previousYaw = state == null ? 0 : state.yaw;
        float previousPitch = state == null ? 0 : state.pitch;
        state = SteppedInterpolationState.initial(position, previousYaw, previousPitch);
        positionBounds = position.copy();
        segmentStartBounds = position.copy();

        clearPath();
        hasUncertainty = !SteppedInterpolationUtils.isPoint(position);
        acknowledgedTransaction = Integer.MIN_VALUE;
    }

    @Override
    public SimpleCollisionBox position() {
        return positionBounds.copy();
    }

    private boolean applyDistantSnap(Vector3d targetPosition, float targetYaw, float targetPitch, int transaction) {
        SimpleCollisionBox previousBounds = positionBounds.copy();
        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            previousBounds.encompass(state.steps.get(stepIndex).positionBounds());
        }

        if (SteppedInterpolationUtils.maxDistanceSquared(previousBounds, targetPosition) <= SNAP_DISTANCE_SQUARED) {
            return false;
        }

        enableProgressBounds();

        SimpleCollisionBox translationBounds = new SimpleCollisionBox(
                Math.min(0, targetPosition.x - previousBounds.maxX),
                Math.min(0, targetPosition.y - previousBounds.maxY),
                Math.min(0, targetPosition.z - previousBounds.maxZ),
                Math.max(0, targetPosition.x - previousBounds.minX),
                Math.max(0, targetPosition.y - previousBounds.minY),
                Math.max(0, targetPosition.z - previousBounds.minZ),
                false
        );

        SteppedInterpolationUtils.expandByTranslation(segmentStartBounds, translationBounds);
        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            SteppedInterpolationState.Step step = state.steps.get(stepIndex);

            SimpleCollisionBox translatedBounds = step.positionBounds().copy();
            SteppedInterpolationUtils.expandByTranslation(translatedBounds, translationBounds);

            state.steps.set(stepIndex, step.withPositionBounds(translatedBounds));
        }

        SimpleCollisionBox targetBounds = state.hasSteps() ? lastStep().positionBounds().copy() : SteppedInterpolationUtils.pointBounds(targetPosition);
        targetBounds.encompass(targetPosition.x, targetPosition.y, targetPosition.z);
        appendOptionalStep(targetPosition, targetYaw, targetPitch, interpolationInterval, targetBounds, transaction);

        positionBounds.encompass(targetPosition.x, targetPosition.y, targetPosition.z);
        return true;
    }

    private void appendPathWithUncertainTarget(SteppedPositionPath path, SimpleCollisionBox previousTargetBounds, Vector3d targetPosition, float targetYaw, float targetPitch, int transaction) {
        SimpleCollisionBox pathBounds = previousTargetBounds.copy();
        int pathDuration = 0;

        for (SteppedPositionPath.Step step : path.getSteps()) {
            pathBounds.encompass(step.x(), step.y(), step.z());
            pathDuration += step.tickOffset();
        }

        appendOptionalStep(targetPosition, targetYaw, targetPitch, Math.max(interpolationInterval, pathDuration), pathBounds, transaction);
        appendOptionalStep(targetPosition, targetYaw, targetPitch, 0, SteppedInterpolationUtils.pointBounds(targetPosition), transaction);
    }

    private void appendOptionalStep(Vector3d targetPosition, float targetYaw, float targetPitch, int optionalDuration, SimpleCollisionBox targetBounds, int transaction) {
        if (optionalDuration == interpolationInterval && state.hasSteps() && SteppedInterpolationUtils.isPoint(targetBounds)
                && mergeDuplicateTail(targetPosition, targetYaw, targetPitch, targetBounds, transaction)) {
            return;
        }

        state.appendOptionalStep(targetPosition, targetYaw, targetPitch, optionalDuration, targetBounds, transaction);
    }

    private boolean mergeDuplicateTail(Vector3d targetPosition, float targetYaw, float targetPitch, SimpleCollisionBox targetBounds, int transaction) {
        SteppedInterpolationState.Step tail = lastStep();
        if (tail.optionalDurationTicks() != interpolationInterval || !SteppedInterpolationUtils.isPoint(tail.positionBounds()) || !tail.position().equals(targetPosition) || tail.yaw() != targetYaw || tail.pitch() != targetPitch) {
            return false;
        }

        state.steps.set(state.steps.size() - 1, new SteppedInterpolationState.Step(targetPosition, targetYaw, targetPitch, 0, transaction, interpolationInterval, targetBounds));
        confirmedDuration = Math.min(confirmedDuration, state.availableDuration - interpolationInterval);

        if (hasUncertainty) {
            progressRange.minimumProgress = Math.min(progressRange.minimumProgress, state.availableDuration - interpolationInterval + 1);
            progressRange.minimumSpeed = 1;
        }

        return true;
    }

    private void tickExactSampler() {
        int previousStepIndex = state.currentStepIndex;
        state.advance(interpolationInterval);
        SteppedInterpolationUtils.setPointBounds(positionBounds, state.currentX, state.currentY, state.currentZ);

        if (!state.hasSteps()) {
            clearPath();
        } else {
            for (int stepIndex = previousStepIndex; stepIndex < state.currentStepIndex; stepIndex++) {
                retiredDuration += state.steps.get(stepIndex).maximumDurationTicks();
            }
        }
    }

    private double skipOptionalStepsForUpperBound() {
        double optionalDuration = 0;
        double segmentStartProgress = retiredDuration;

        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            SteppedInterpolationState.Step step = state.steps.get(stepIndex);
            optionalDuration += step.optionalDurationTicks();

            if (step.optionalDurationTicks() != 0 && segmentStartProgress >= upperBoundSkippedThrough && progressRange.maximumProgress >= segmentStartProgress) {
                progressRange.maximumProgress += step.optionalDurationTicks();
                upperBoundSkippedThrough = segmentStartProgress + step.maximumDurationTicks();
            }

            segmentStartProgress += step.maximumDurationTicks();
        }

        return optionalDuration;
    }

    private void enableProgressBounds() {
        if (hasUncertainty) {
            return;
        }

        hasUncertainty = true;
        progressRange.reset();
        if (!state.hasSteps()) {
            return;
        }

        double initialProgress = retiredDuration + state.nextSampleTicks;
        progressRange.minimumProgress = initialProgress;
        progressRange.maximumProgress = initialProgress;
        progressRange.minimumSpeed = state.interpolationSpeed;
        progressRange.maximumSpeed = state.interpolationSpeed;

        double optionalDuration = 0;
        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            optionalDuration += state.steps.get(stepIndex).optionalDurationTicks();
        }

        progressRange.roundingError = Math.abs(state.availableDuration - progressRange.minimumProgress + 1 - optionalDuration - state.remainingTicks);
        SteppedInterpolationUtils.setPointBounds(segmentStartBounds, state.segmentStartX, state.segmentStartY, state.segmentStartZ);
    }

    private void samplePositionBounds(double minimumProgress, double maximumProgress) {
        SteppedInterpolationUtils.clearBounds(positionBounds);
        double segmentStartProgress = retiredDuration;
        SimpleCollisionBox startBounds = segmentStartBounds;

        if (minimumProgress <= retiredDuration) {
            positionBounds.encompass(startBounds);
        }

        for (int stepIndex = state.currentStepIndex; stepIndex < state.steps.size(); stepIndex++) {
            SteppedInterpolationState.Step step = state.steps.get(stepIndex);
            SimpleCollisionBox endBounds = step.positionBounds();
            int durationTicks = step.maximumDurationTicks();

            if (segmentStartProgress + durationTicks >= minimumProgress && segmentStartProgress <= maximumProgress) {
                float minimumSegmentProgress = SteppedInterpolationUtils.segmentProgress(minimumProgress, segmentStartProgress, durationTicks);
                float maximumSegmentProgress = SteppedInterpolationUtils.segmentProgress(maximumProgress, segmentStartProgress, durationTicks);

                SteppedInterpolationUtils.encompassInterpolatedBounds(positionBounds, startBounds, endBounds, minimumSegmentProgress);
                SteppedInterpolationUtils.encompassInterpolatedBounds(positionBounds, startBounds, endBounds, maximumSegmentProgress);
            }

            segmentStartProgress += durationTicks;
            startBounds = endBounds;
            if (segmentStartProgress > maximumProgress) {
                break;
            }
        }
    }

    private void retireCompletedSteps(double minimumProgress) {
        while (state.hasSteps()) {
            SteppedInterpolationState.Step step = state.steps.get(state.currentStepIndex);
            if (step.transaction() > acknowledgedTransaction || retiredDuration + step.maximumDurationTicks() > minimumProgress) {
                break;
            }

            retiredDuration += step.maximumDurationTicks();
            SteppedInterpolationUtils.copyBounds(step.positionBounds(), segmentStartBounds);
            state.currentStepIndex++;
        }
    }

    private void finishInterpolation() {
        SteppedInterpolationUtils.copyBounds(lastStep().positionBounds(), positionBounds);

        state.snap(state.targetPosition, state.targetYaw, state.targetPitch);
        state.remainingTicks = 0;
        state.interpolationSpeed = 1;

        clearPath();
        hasUncertainty = !SteppedInterpolationUtils.isPoint(positionBounds);
    }

    private SteppedInterpolationState.Step lastStep() {
        return state.steps.get(state.steps.size() - 1);
    }

    private void clearPath() {
        state.steps.clear();
        state.currentStepIndex = 0;
        retiredDuration = 0;
        state.availableDuration = 0;
        confirmedDuration = 0;
        upperBoundSkippedThrough = 0;
        progressRange.reset();
    }
}
