package org.example.tentrilliondollars.kakaopay.service;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.tentrilliondollars.kakaopay.dto.request.CancelRequestDto;
import org.example.tentrilliondollars.kakaopay.dto.request.PayInfoDto;
import org.example.tentrilliondollars.kakaopay.dto.request.PayRequestDto;
import org.example.tentrilliondollars.kakaopay.dto.response.CancelResDto;
import org.example.tentrilliondollars.kakaopay.dto.response.PayApproveResDto;
import org.example.tentrilliondollars.kakaopay.dto.response.PayReadyResDto;
import org.example.tentrilliondollars.order.dto.OrderDetailResponseDto;
import org.example.tentrilliondollars.order.entity.Order;
import org.example.tentrilliondollars.order.entity.OrderDetail;
import org.example.tentrilliondollars.order.entity.OrderState;
import org.example.tentrilliondollars.order.repository.OrderDetailRepository;
import org.example.tentrilliondollars.order.repository.OrderRepository;
import org.example.tentrilliondollars.order.service.OrderService;
import org.example.tentrilliondollars.product.entity.Product;
import org.example.tentrilliondollars.product.service.ProductService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@Service
@Slf4j
public class KakaoPayService {

    private final MakeRequest makeRequest;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RedissonClient redissonClient;
    private final OrderDetailRepository orderDetailRepository;
    private final EntityManager entityManager;
    private final ProductService productService;
    @Value("${kakao.api.admin-key}")
    private String adminKey;

    @Transactional
    public PayReadyResDto getRedirectUrl(Long orderId) throws Exception {
        Order order = orderRepository.getReferenceById(orderId);
        if (order.getState() != OrderState.NOTPAYED) {
            throw new IllegalStateException("주문 상태가 결제 대기 상태가 아닙니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        String auth = "KakaoAK " + adminKey;
        headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization", auth);

        PayRequestDto payRequestDto = makeRequest.getReadyRequest(createPayInfo(orderId),orderId);
        HttpEntity<MultiValueMap<String, String>> urlRequest = new HttpEntity<>(
            payRequestDto.getMap(), headers);
        RestTemplate rt = new RestTemplate();
        PayReadyResDto payReadyResDto = rt.postForObject(payRequestDto.getUrl(), urlRequest,
            PayReadyResDto.class);
        orderRepository.getReferenceById(orderId).updateTid(payReadyResDto.getTid());
        return payReadyResDto;
    }
    //

    @Transactional
    public PayApproveResDto getApprove(String pgToken, Long orderId) throws Exception {
        Order order = orderRepository.getReferenceById(orderId);
        String tid = order.getKakaoTid();
        HttpHeaders headers = new HttpHeaders();
        String auth = "KakaoAK " + adminKey;
        headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization", auth);
        PayRequestDto payRequestDto = makeRequest.getApproveRequest(tid, pgToken,orderId);
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(payRequestDto.getMap(), headers);
        RestTemplate rt = new RestTemplate();
        PayApproveResDto payApproveResDto = rt.postForObject(payRequestDto.getUrl(), requestEntity,
            PayApproveResDto.class);
        //밑에서 결제 완료 후 상태 업데이트
        //여기서
        System.out.println("카카오 서비스 실행");
        //주문 상태가 NOTPAY인지 확인
        Map<Long, Long> basket = getBasketFromOrder(order);
        entityManager.clear();
        for (Long productId : basket.keySet()) {
            String lockKey = "product_lock:" + productId;
            RLock lock = redissonClient.getLock(lockKey);
            try {
                boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
                if (!isLocked) {
                    throw new RuntimeException("락 획득에 실패했습니다.");
                }
                orderService.updateStockAndCreateOrderDetail(productId, basket.get(productId));
                order.changeState(OrderState.PREPARING);
                orderRepository.save(order);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 재설정
                throw new RuntimeException("락 획득 중 오류가 발생했습니다.", e);
            } finally {
                if (lock.isLocked()) {
                    lock.unlock();
                }
            }
        }
        // 여기까지
        return payApproveResDto;
    }

    private Map<Long, Long> getBasketFromOrder(Order order) {
        return orderDetailRepository.findByOrderId(order.getId())
            .stream()
            .collect(Collectors.toMap(
                OrderDetail::getProductId,
                OrderDetail::getQuantity
            ));
    }

    @Transactional
    public CancelResDto kakaoCancel(Long orderId) {
        String tid = orderRepository.getReferenceById(orderId).getKakaoTid();
        HttpHeaders headers = new HttpHeaders();
        String auth = "KakaoAK " + adminKey;
        headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization", auth);
        CancelRequestDto cancelRequestDto = makeRequest.getCancelRequest(tid,
            orderService.getTotalPrice(orderId));
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(
            cancelRequestDto.getMap(), headers);
        RestTemplate rt = new RestTemplate();
        CancelResDto cancelResDto = rt.postForObject(cancelRequestDto.getUrl(), requestEntity,
            CancelResDto.class);
        List<OrderDetailResponseDto> orderDetailList=orderService.getOrderDetailList(orderId);
        for(OrderDetailResponseDto responseDto:orderDetailList){
            Long productId = responseDto.getProductId();
            Product product = productService.getProduct(productId);
            product.updateStockAfterOrder(-responseDto.getQuantity());
        }
        orderService.deleteOrder(orderId);
        return cancelResDto;
    }

    public PayInfoDto createPayInfo(Long orderId) {
        PayInfoDto payInfoDto = new PayInfoDto();
        payInfoDto.setPrice(orderService.getTotalPrice(orderId));
        payInfoDto.setItemName("TenCompany");
        return payInfoDto;
    }

    @Transactional
    public void getApproveTest(Long orderId) throws Exception {
        Order order = orderRepository.getReferenceById(orderId);
        //밑에서 결제 완료 후 상태 업데이트
        //여기서    System.out.println("카카오 서비스 실행");
        //주문 상태가 NOTPAY인지 확인
        if (order.getState() != OrderState.NOTPAYED) {
            throw new IllegalStateException("주문 상태가 결제 대기 상태가 아닙니다.");
        }
        Map<Long, Long> basket = getBasketFromOrder(order);
        entityManager.clear();
        for (Long productId : basket.keySet()) {
            String lockKey = "product_lock:" + productId;
            RLock lock = redissonClient.getLock(lockKey);
            try {
                boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
                if (!isLocked) {
                    throw new RuntimeException("락 획득에 실패했습니다.");
                }
                orderService.updateStockAndCreateOrderDetail(productId, basket.get(productId));
                order.changeState(OrderState.PREPARING);
                orderRepository.save(order);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 재설정
                throw new RuntimeException("락 획득 중 오류가 발생했습니다.", e);
            } finally {
                if (lock.isLocked()) {
                    lock.unlock();
                }
            }
        }
        // 여기까지
    }
}


