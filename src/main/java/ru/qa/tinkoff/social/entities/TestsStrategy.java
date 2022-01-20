package ru.qa.tinkoff.social.entities;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Accessors(chain = true)
@Data
public class TestsStrategy implements Serializable {
    String id;
}