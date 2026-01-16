package com.example.demo.model;

/**
 * User roles for authorization.
 */
public enum Role {
    USER,       // Regular user - can only manage their own files
    ADMIN       // Administrator - can manage all users and files
}
