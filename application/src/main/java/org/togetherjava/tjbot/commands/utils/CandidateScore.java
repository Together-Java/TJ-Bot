package org.togetherjava.tjbot.commands.utils;

record CandidateScore(String candidate, int score) implements Comparable<CandidateScore> {
    @Override
    public int compareTo(CandidateScore otherCandidateScore) {
        return Integer.compare(this.score, otherCandidateScore.score);
    }
}
