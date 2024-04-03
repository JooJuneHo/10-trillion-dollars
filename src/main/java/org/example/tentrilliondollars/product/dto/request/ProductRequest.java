package org.example.tentrilliondollars.product.dto.request;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductRequest {

    private String name;

    private Long price;

    private String description;

    private Long stock;

    private String photo;
}
