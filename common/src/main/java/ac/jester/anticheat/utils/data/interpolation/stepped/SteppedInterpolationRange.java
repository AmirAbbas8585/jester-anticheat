package ac.jester.anticheat.utils.data.interpolation.stepped;

public final class SteppedInterpolationRange {
    public double minimumProgress = 1;
    public double maximumProgress = 1;
    public double minimumSpeed = 1;
    public double maximumSpeed = 1;

    public double sampledMinimumProgress;
    public double sampledMaximumProgress;
    public double roundingError;

    public void tick(double confirmedDuration, double availableDuration, double retiredDuration, int interpolationInterval, double optionalDuration) {
        sampledMinimumProgress = Math.min(minimumProgress, confirmedDuration);
        sampledMaximumProgress = Math.min(maximumProgress, availableDuration);

        double clockRoundingError = 16 * Math.ulp((float) (availableDuration - retiredDuration + 1));
        roundingError += clockRoundingError;

        double minimumRemainingDuration = Math.max(0, confirmedDuration - maximumProgress + 1 - optionalDuration - roundingError);
        double maximumRemainingDuration = Math.max(0, availableDuration - minimumProgress + 1 + roundingError);
        double slowestSpeed = nextSpeed(minimumSpeed, minimumRemainingDuration, interpolationInterval);
        double fastestSpeed = nextSpeed(maximumSpeed, maximumRemainingDuration, interpolationInterval);

        double nextMinimumProgress = minimumProgress + nextSpeed(minimumSpeed, Math.max(0, confirmedDuration - minimumProgress + 1 - optionalDuration - roundingError), interpolationInterval);
        double nextMaximumProgress = maximumProgress + nextSpeed(maximumSpeed, Math.max(0, availableDuration - maximumProgress + 1 + roundingError), interpolationInterval);

        boolean mayFinish = maximumProgress + fastestSpeed >= confirmedDuration + 1 - optionalDuration - roundingError;
        boolean mustFinish = minimumProgress + slowestSpeed >= availableDuration + 1 + roundingError;
        double speedRoundingError = 8 * Math.ulp((float) fastestSpeed);

        minimumSpeed = mayFinish ? 1 : Math.max(1, slowestSpeed - speedRoundingError);
        maximumSpeed = mustFinish ? 1 : fastestSpeed + speedRoundingError;

        minimumProgress = Math.max(sampledMinimumProgress, Math.min(confirmedDuration + 1 - roundingError, nextMinimumProgress) - clockRoundingError);
        maximumProgress = Math.min(availableDuration + 1 + roundingError, nextMaximumProgress) + clockRoundingError;
    }

    public void skipTicks(double availableDuration, double retiredDuration, int interpolationInterval) {
        roundingError += 16 * Math.ulp((float) (availableDuration - retiredDuration + 1)) * (availableDuration - retiredDuration + 1);
        maximumSpeed = Math.max(maximumSpeed, Math.max(1, (availableDuration - minimumProgress + 1 + roundingError) / interpolationInterval));
        minimumSpeed = 1;
        maximumProgress = availableDuration + 1 + roundingError;
        sampledMaximumProgress = availableDuration;
    }

    public void reset() {
        minimumProgress = 1;
        maximumProgress = 1;
        minimumSpeed = 1;
        maximumSpeed = 1;
        sampledMinimumProgress = 0;
        sampledMaximumProgress = 0;
        roundingError = 0;
    }

    private static double nextSpeed(double previousSpeed, double remainingDuration, int interpolationInterval) {
        return previousSpeed + (Math.max(remainingDuration / interpolationInterval, 1) - previousSpeed) / interpolationInterval;
    }
}
