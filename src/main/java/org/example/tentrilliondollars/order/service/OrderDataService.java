package org.example.tentrilliondollars.order.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.tentrilliondollars.order.dto.OrderDetailAdminResponse;
import org.example.tentrilliondollars.order.entity.Order;
import org.example.tentrilliondollars.order.entity.OrderDetail;
import org.example.tentrilliondollars.order.repository.OrderDetailRepository;
import org.example.tentrilliondollars.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderDataService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    //**********************productService에서 필요한 메서드**************************//
    public List<OrderDetail> getOrderDetailListUseProductIdList(List<Long> productIds){
        List<OrderDetail> orderDetails = orderDetailRepository.findByProductIdIn(productIds);
        return orderDetails;
    }

    public List<Long> getOrderIdList(List<OrderDetail> orderDetails){
        List<Long> orderIds = orderDetails.stream()
            .map(OrderDetail::getOrderId)
            .distinct()
            .collect(Collectors.toList());
        return orderIds;
    }
    public Map<Long, Order> getOrderMap(List<Long> orderIds) {
        List<Order> orders = orderRepository.findByIdIn(orderIds);
        Map<Long,Order> orderMap = orders.stream().collect(Collectors.toMap(Order::getId,order -> order));
        return orderMap;
    }
    public Map<Long, List<OrderDetailAdminResponse>> getOrderDetailAdminResponse(Map<Long,Order> orderMap,List<OrderDetail> orderDetails) {
        Map<Long, List<OrderDetailAdminResponse>> orderDetailsMap = orderDetails.stream()
            .collect(Collectors.groupingBy(OrderDetail::getProductId,
                Collectors.mapping(od -> new OrderDetailAdminResponse(od, orderMap.get(od.getOrderId())), Collectors.toList())));
        return orderDetailsMap;

    }
}
