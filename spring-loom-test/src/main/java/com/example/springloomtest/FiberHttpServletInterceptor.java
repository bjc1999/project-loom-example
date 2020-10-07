package com.example.springloomtest;


@FunctionalInterface
public interface FiberHttpServletInterceptor<T, U> {
    void accept(T t, U u) throws Exception;
}
