package com.smartrecruit.user.model;

/**
 * State of the AI analysis of a candidate's CV (BE-CV-02): {@code NONE} no CV, {@code PENDING} uploaded and
 * being analysed, {@code DONE} analysed, {@code FAILED} the AI failed (the upload itself stays valid).
 */
public enum CvAnalysisStatus {
    NONE, PENDING, DONE, FAILED
}
