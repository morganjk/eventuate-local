package io.eventuate.local.postgres.binlog;

import io.eventuate.local.common.BinlogFileOffset;
import io.eventuate.local.common.EventuateConfigurationProperties;
import io.eventuate.local.java.kafka.EventuateKafkaConfigurationProperties;
import io.eventuate.local.java.kafka.producer.EventuateKafkaProducer;
import io.eventuate.local.test.util.AbstractCdcTest;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = PostgresBinlogCdcIntegrationTestConfiguration.class)
@IntegrationTest
public class DatabaseBinlogOffsetKafkaStoreTest extends AbstractCdcTest {

  @Autowired
  EventuateKafkaProducer eventuateKafkaProducer;

  @Autowired
  EventuateConfigurationProperties eventuateConfigurationProperties;

  @Autowired
  EventuateKafkaConfigurationProperties eventuateKafkaConfigurationProperties;


  @Test
  public void shouldSendBinlogFilenameAndOffset() throws InterruptedException {
    generateAndSaveBinlogFileOffset();
  }

  @Test
  public void shouldGetEmptyOptionalFromEmptyTopic() {
    DatabaseLastSequenceNumberKafkaStore databaseLastSequenceNumberKafkaStore = getDatabaseLastSequenceNumberKafkaStore(UUID.randomUUID().toString(), "mySqlBinaryLogClientName");
    databaseLastSequenceNumberKafkaStore.getLastBinlogFileOffset().isPresent();
    databaseLastSequenceNumberKafkaStore.stop();
  }

  @Test
  public void shouldWorkCorrectlyWithMultipleDifferentNamedBinlogs() throws InterruptedException {
    floodTopic(eventuateConfigurationProperties.getDbHistoryTopicName(), "mySqlBinaryLogClientName1");

    generateAndSaveBinlogFileOffset();
  }

  @Test
  public void shouldReadTheLastRecordMultipleTimes() throws InterruptedException {
    BinlogFileOffset bfo = generateAndSaveBinlogFileOffset();

    assertLastRecordEquals(bfo);
    assertLastRecordEquals(bfo);
  }

  private void floodTopic(String topicName, String key) {
    Producer<String, String> producer = createProducer(eventuateKafkaConfigurationProperties.getBootstrapServers());
    for (int i = 0; i < 10; i++)
      producer.send(new ProducerRecord<>(topicName, key, Integer.toString(i)));

    producer.close();
  }

  public DatabaseLastSequenceNumberKafkaStore getDatabaseLastSequenceNumberKafkaStore(String topicName, String key) {
    return new DatabaseLastSequenceNumberKafkaStore(topicName, key, eventuateKafkaProducer, eventuateKafkaConfigurationProperties);
  }

  private BinlogFileOffset generateAndSaveBinlogFileOffset() throws InterruptedException {
    BinlogFileOffset bfo = generateBinlogFileOffset();
    DatabaseLastSequenceNumberKafkaStore binlogOffsetKafkaStore = getDatabaseLastSequenceNumberKafkaStore(eventuateConfigurationProperties.getDbHistoryTopicName(), "mySqlBinaryLogClientName");
    binlogOffsetKafkaStore.save(bfo);

    Thread.sleep(5000);

    BinlogFileOffset savedBfo = binlogOffsetKafkaStore.getLastBinlogFileOffset().get();
    assertEquals(bfo, savedBfo);
    binlogOffsetKafkaStore.stop();
    return savedBfo;
  }

  private void assertLastRecordEquals(BinlogFileOffset binlogFileOffset) {
    DatabaseLastSequenceNumberKafkaStore binlogOffsetKafkaStore = getDatabaseLastSequenceNumberKafkaStore(eventuateConfigurationProperties.getDbHistoryTopicName(), "mySqlBinaryLogClientName");

    BinlogFileOffset lastRecord = binlogOffsetKafkaStore.getLastBinlogFileOffset().get();
    assertEquals(binlogFileOffset, lastRecord);
    binlogOffsetKafkaStore.stop();
  }
}