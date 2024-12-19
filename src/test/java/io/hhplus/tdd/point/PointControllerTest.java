package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


@SpringBootTest
@AutoConfigureMockMvc
class PointControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void 존재하지_않는_사용자의_포인트_조회시_0포인트를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/point/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(0));
    }

    @Test
    void 포인트_충전과_사용_후_이력을_조회하면_모든_이력이_순서대로_조회된다() throws Exception {
        // given
        long userId = 1L;
        long chargeAmount = 1000L;
        long useAmount = 500L;

        // 포인트 충전
        mockMvc.perform(patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.valueOf(chargeAmount)));

        // 포인트 사용
        mockMvc.perform(patch("/point/{id}/use", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.valueOf(useAmount)));

        // when & then
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[0].amount").value(chargeAmount))
                .andExpect(jsonPath("$[1].type").value("USE"))
                .andExpect(jsonPath("$[1].amount").value(useAmount));
    }

    @Test
    void 포인트_충전시_정상적으로_충전되면_업데이트된_포인트를_반환한다() throws Exception {
        // given
        long userId = 2L;
        long amount = 1000L;

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(amount));
    }

    @Test
    void 포인트_사용시_정상적으로_사용되면_차감된_포인트를_반환한다() throws Exception {
        // given
        long userId = 4L;
        long chargeAmount = 5000L;
        long useAmount = 3000L;

        // 포인트 충전
        mockMvc.perform(patch("/point/{id}/charge", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.valueOf(chargeAmount)));

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(useAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(chargeAmount - useAmount));
    }

    @Test
    void 포인트_사용시_잔액이_부족하면_IllegalArgumentException을_반환한다() throws Exception {
        // given
        long userId = 3L;
        long useAmount = 1000L;

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(useAmount)))
                .andExpect(status().is5xxServerError()) //handle exception
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof IllegalArgumentException))
                .andExpect(result -> assertEquals("포인트가 부족합니다.",
                        result.getResolvedException().getMessage()));
    }
}