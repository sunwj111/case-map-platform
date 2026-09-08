package com.casemap.hierarchy.web;

import com.casemap.hierarchy.produce.BatchReviewResult;
import com.casemap.hierarchy.produce.PublishBatchResult;
import com.casemap.hierarchy.produce.ReviewItem;
import com.casemap.hierarchy.produce.ReviewItemKind;
import com.casemap.hierarchy.produce.ReviewQueueResult;
import com.casemap.hierarchy.produce.ReviewService;
import com.casemap.hierarchy.produce.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ReviewService reviewService;

    @Test
    void queriesAndUpdatesReviewQueue() throws Exception {
        ReviewItem reviewItem = reviewItem("RV-001", ReviewStatus.PENDING);
        when(reviewService.query(
                "batch-001",
                null,
                ReviewStatus.PENDING,
                ReviewItemKind.HISTORICAL_CASE,
                null,
                "审核"
        ))
                .thenReturn(new ReviewQueueResult(1, 1, 0, 0, 0, List.of(reviewItem)));
        when(reviewService.update(eq("RV-001"), any())).thenReturn(reviewItem);

        mockMvc.perform(get("/api/v1/reviews")
                        .param("batchId", "batch-001")
                        .param("status", "待评审")
                        .param("kind", "历史用例")
                        .param("keyword", "审核"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("RV-001"));

        mockMvc.perform(patch("/api/v1/reviews/RV-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene": "造价审核",
                                  "feature": "自动审核",
                                  "operator": "测试评审人"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("待评审"));
    }

    @Test
    void confirmsBatchAndPublishesAssets() throws Exception {
        when(reviewService.confirmBatch(any())).thenReturn(new BatchReviewResult(
                2,
                1,
                1,
                List.of("RV-001"),
                List.of(new BatchReviewResult.BatchReviewFailure("RV-002", "缺口用例步骤不能为空"))
        ));
        when(reviewService.publishBatch(eq("batch-001"), any())).thenReturn(new PublishBatchResult(
                "batch-001",
                1,
                List.of("OFF-001"),
                "2026-09-07T17:00:00Z"
        ));

        mockMvc.perform(post("/api/v1/reviews/batch/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewIds": ["RV-001", "RV-002"],
                                  "operator": "测试评审人"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));

        mockMvc.perform(post("/api/v1/reviews/batches/batch-001/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"测试评审人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(1))
                .andExpect(jsonPath("$.officialCaseIds[0]").value("OFF-001"));

        verify(reviewService).publishBatch(eq("batch-001"), any());
    }

    private static ReviewItem reviewItem(String id, ReviewStatus status) {
        ReviewItem reviewItem = new ReviewItem();
        reviewItem.setId(id);
        reviewItem.setBatchId("batch-001");
        reviewItem.setKind(ReviewItemKind.HISTORICAL_CASE);
        reviewItem.setStatus(status);
        reviewItem.setCaseName("造价审核");
        reviewItem.setScene("造价审核");
        reviewItem.setFeature("自动审核");
        return reviewItem;
    }
}
