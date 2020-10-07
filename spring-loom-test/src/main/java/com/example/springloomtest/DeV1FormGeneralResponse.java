package com.example.springloomtest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public abstract class DeV1FormGeneralResponse {
    private String requestId;
    private String message;
}
