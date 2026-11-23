package com.example.comunidadconectada.ui;

import com.example.comunidadconectada.R;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.example.comunidadconectada.data.CommunityRepository.OperationFailure;
import com.google.firebase.database.DatabaseError;

public final class Errors {
    private Errors() {}

    public static int message(Throwable failure, int fallback) {
        Throwable cause = failure;
        while (cause instanceof RuntimeExecutionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof FirebaseNetworkException) return R.string.connection_error;
        if (cause instanceof FirebaseTooManyRequestsException) return R.string.rate_limit;
        if (cause instanceof OperationFailure failureWithCode) {
            return switch (failureWithCode.code()) {
                case DatabaseError.PERMISSION_DENIED, DatabaseError.EXPIRED_TOKEN,
                     DatabaseError.INVALID_TOKEN -> R.string.denied;
                case DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR,
                     DatabaseError.UNAVAILABLE -> R.string.connection_error;
                default -> fallback;
            };
        }
        return fallback;
    }

    public static String validationMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof RuntimeExecutionException && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof IllegalArgumentException ? cause.getMessage() : null;
    }
}
