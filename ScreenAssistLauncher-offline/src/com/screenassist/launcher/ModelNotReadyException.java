package com.screenassist.launcher;

public class ModelNotReadyException extends RuntimeException {
    public ModelNotReadyException(String msg) { super(msg); }
    public ModelNotReadyException(String msg, Throwable cause) { super(msg, cause); }
}