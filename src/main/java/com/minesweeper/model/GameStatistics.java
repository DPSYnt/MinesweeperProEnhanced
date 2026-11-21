package com.minesweeper.model;

public record GameStatistics(int totalMoves, int correctFlags, int wrongFlags,
                             int minesCleared, int lifetimeMinesCleared, long elapsedSeconds) {
    public double flagAccuracy() {
        int attempts = correctFlags + wrongFlags;
        return attempts == 0 ? 0 : correctFlags * 100.0 / attempts;
    }
}
