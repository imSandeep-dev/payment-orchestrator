package com.payflow.orchestrator.gateway;

/** Maps 1:1 onto Section B4.3's X-Mock-Response header values. */
public enum MockResponseType {
    SUCCESS, TIMEOUT, SERVER_ERROR, DECLINE, RATE_LIMIT
}