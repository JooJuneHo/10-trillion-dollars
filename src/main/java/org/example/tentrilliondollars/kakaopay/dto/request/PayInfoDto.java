package org.example.tentrilliondollars.kakaopay.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PayInfoDto {
    private Long price;
    private String itemName;
    private Long orderId;
}
