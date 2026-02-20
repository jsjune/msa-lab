package org.example.admin.service;

public class DuplicatePolicyException extends RuntimeException {

    public DuplicatePolicyException(String message) {
        super(message);
    }
}