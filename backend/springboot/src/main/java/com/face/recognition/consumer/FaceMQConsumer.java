package com.face.recognition.consumer;

import com.face.recognition.model.FaceDataMessage;
import com.face.recognition.service.FaceStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FaceMQConsumer {

    @Autowired
    private FaceStorageService storageService;

    /**
     * 监听人脸数据队列
     */
    @RabbitListener(queues = "face_recognition_queue")
    public void receiveMessage(FaceDataMessage message) {
        log.info("📥 收到来自 MQ 的人脸数据消息: 地点: {}, 向量维度: {}",
                message.getLocation(), message.getVector().size());

        try {
            // 调用业务逻辑进行双库保存
            storageService.saveFaceData(message);
        } catch (Exception e) {
            log.error("❌ 处理 MQ 消息时发生错误: ", e);
            // 这里可以增加死信队列处理逻辑
        }
    }
}
