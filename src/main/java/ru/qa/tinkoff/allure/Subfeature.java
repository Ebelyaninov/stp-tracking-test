package ru.qa.tinkoff.allure;

import io.qameta.allure.LabelAnnotation;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@LabelAnnotation(name = "subfeature")
public @interface Subfeature {

    String value();
}
