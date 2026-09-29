package org.albedo.vllmpt.module.chat.model.vo;


public record QuotaReservation(String userId, String quotaKey, String statDate,
                                  long estimateTokens, boolean enabled){

}
