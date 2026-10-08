package com.example.carsharing.messaging;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void failedPublicationDoesNotMarkEventDelivered() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var row = mock(java.sql.ResultSet.class);
        when(row.getLong(1)).thenReturn(11L);
        when(row.getLong(2)).thenReturn(42L);
        when(row.getString(3)).thenReturn("payload");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation ->
                List.of(((RowMapper) invocation.getArgument(1)).mapRow(row, 0)));
        when(kafka.send("finished", "42", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker offline")));
        assertThatThrownBy(() -> new OutboxRelay(jdbc, kafka, "finished").publish())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("retained for retry");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void marksDeliveredOnlyAfterAcknowledgedSend() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var row = mock(java.sql.ResultSet.class);
        when(row.getLong(1)).thenReturn(11L);
        when(row.getLong(2)).thenReturn(42L);
        when(row.getString(3)).thenReturn("payload");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation ->
                List.of(((RowMapper) invocation.getArgument(1)).mapRow(row, 0)));
        when(kafka.send("finished", "42", "payload"))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String,String>) null));
        new OutboxRelay(jdbc, kafka, "finished").publish();
        var order = inOrder(kafka, jdbc);
        order.verify(kafka).send("finished", "42", "payload");
        order.verify(jdbc).update("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP(6) WHERE id=?", 11L);
    }
}