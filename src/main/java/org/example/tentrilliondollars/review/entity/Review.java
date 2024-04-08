package org.example.tentrilliondollars.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.tentrilliondollars.global.TimeStamped;
import org.example.tentrilliondollars.product.entity.Product;
import org.example.tentrilliondollars.review.dto.ReviewRequest;
import org.example.tentrilliondollars.user.entity.User;

@Entity
@Table(name="review")
@Getter
@NoArgsConstructor
public class Review extends TimeStamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String content;
    @Column
    private String photo;
    @Column
    private int score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prodcut_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;


    public Review(ReviewRequest reviewRequest,Product product,User user){
        this.content = reviewRequest.getContent();
        this.photo = reviewRequest.getPhoto();
        this.score = reviewRequest.getScore();
        this.product = product;
        this.user = user;
    }
    public void updateReview(ReviewRequest reviewRequest) {
        this.content = reviewRequest.getContent();
        this.photo = reviewRequest.getPhoto();
        this.score = reviewRequest.getScore();
    }
    public void updateImageId(String imageId){
        this.photo =imageId;
    }

}
