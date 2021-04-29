package ru.qa.tinkoff.social.entities;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigInteger;
import java.util.UUID;

@Data
@Accessors(chain = true)
@Entity
@Table(name = "profile", schema = "social")
public class Profile {
    @Id
    UUID id;

    @Column(name = "nickname")
    String nickname;

    @Column(name = "position")
    BigInteger position;

    @Column(name = "siebel_id")
    String siebelId;

    @Column(name = "status")
    String status;

    @Column(name = "image")
    UUID image;

    @Column(name = "has_contract")
    Boolean hasContract;

    @Column(name = "deleted")
    Boolean deleted;

    @Column(name = "block")
    Boolean block;

/*
    @Column(name = "description")
    String description;

    @Column(name = "dictionary_ids")
    int dictionaryIds;

    @Column(name = "service_tags")
    String serviceTags;

    @Column(name = "interested_tags")
    String interestedTags;

    @Column(name = "interesting_tags")
    String interestingTags;

    @Column(name = "followers_count")
    int followersCount;

    @Column(name = "following_count")
    int followingCount;

    @Column(name = "validated")
    Boolean validated;

    @Column(name = "interested_bitmap")
    int interestedBitmap;

    @Column(name = "interesting_bitmap")
    int interestingBitmap;

    @Column(name = "inserted")
    Timestamp inserted;

    @Column(name = "updated")
    Timestamp updated;

    @Column(name = "registered_at")
    Timestamp registeredAt;

    @Column(name = "settings")
    String settings;

    @Column(name = "advisable_position")
    int advisablePosition;

    @Column(name = "hashtag_following_count")
    int hashtagFollowingCount;

    @Column(name = "muted_profiles_count")
    int mutedProfilesCount;
 */
}
