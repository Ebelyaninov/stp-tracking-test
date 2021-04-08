package ru.qa.tinkoff.social.entities;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Accessors(chain = true)
@Data
public class SocialProfile implements Serializable {
    String id;
    String nickname;
    String image;
}
