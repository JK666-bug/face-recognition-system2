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
        if ("VIDEO".equalsIgnoreCase(message.getType())) {
            log.info("Received [VIDEO] message from MQ: ID: {}, Size: {} KB",
                    message.getVideo_id(),
                    (message.getVideo_base64() != null ? message.getVideo_base64().length() / 1024 : 0));
        } else {
            log.info("Received [FACE] message from MQ: Location: {}, Vector Dimension: {}",
                    message.getLocation(),
                    (message.getVector() != null ? message.getVector().size() : 0));
        }

        try {
            // 调用业务逻辑进行双库保存
            storageService.saveFaceData(message);
            log.info("Message processing completed: ID: {}",
                    ("VIDEO".equalsIgnoreCase(message.getType()) ? message.getVideo_id() : "FACE"));
        } catch (Exception e) {
            log.error("Error processing MQ message [ID: {}]: ",
                    ("VIDEO".equalsIgnoreCase(message.getType()) ? message.getVideo_id() : "FACE"), e);
            // 这里可以增加死信队列处理逻辑
        }
    }
}
