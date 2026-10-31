package com.dayo.careround.authservice.utils;

import com.dayo.careround.authservice.data.models.enums.DoctorGrade;
import com.dayo.careround.authservice.data.models.enums.NurseGrade;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.exception.InvalidGradeException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The grade travels to user-service in user.registered, which parses it with DoctorGrade / NurseGrade. Those enums
 * (copied verbatim here) are the canonical lists, so anything else is rejected at create-user instead of creating
 * an account whose profile user-service can never build.
 */
public final class GradeValidator {

    private GradeValidator() {}

    public static void validate(UserRole role, String grade) {
        switch (role) {
            case DOCTOR -> requireOneOf(role, grade, DoctorGrade.class);
            case NURSE -> requireOneOf(role, grade, NurseGrade.class);
            default -> {
                if (grade != null && !grade.isBlank()) {
                    throw new InvalidGradeException(role + " accounts have no grade; omit it");
                }
            }
        }
    }

    private static <E extends Enum<E>> void requireOneOf(UserRole role, String grade, Class<E> grades) {
        String allowed = Arrays.stream(grades.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
        if (grade == null || grade.isBlank()) {
            throw new InvalidGradeException("grade is required for " + role + " accounts. Allowed: " + allowed);
        }
        boolean known = Arrays.stream(grades.getEnumConstants()).anyMatch(g -> g.name().equals(grade));
        if (!known) {
            throw new InvalidGradeException("Invalid grade '" + grade + "' for " + role + ". Allowed: " + allowed);
        }
    }
}
