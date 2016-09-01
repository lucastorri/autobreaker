package com.unstablebuild.autobreaker.guice;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface WithCircuitBreaker {

}
