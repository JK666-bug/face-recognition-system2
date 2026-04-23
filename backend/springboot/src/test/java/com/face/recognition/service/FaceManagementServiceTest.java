package com.face.recognition.service;

import com.face.recognition.dto.PeopleRecordDTO;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FaceManagementService: create person from upload, delete person, merge validation branches.
 */
@ExtendWith(MockitoExtension.class)
class FaceManagementServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MilvusServiceClient milvusClient;

    @Mock
    private FaceSearchService faceSearchService;

    private FaceManagementService faceManagementService;

    @BeforeEach
    void setUp() {
        faceManagementService = new FaceManagementService();
        ReflectionTestUtils.setField(faceManagementService, "minioClient", minioClient);
        ReflectionTestUtils.setField(faceManagementService, "milvusClient", milvusClient);
        ReflectionTestUtils.setField(faceManagementService, "faceSearchService", faceSearchService);
        ReflectionTestUtils.setField(faceManagementService, "bucketName", "faces");
        ReflectionTestUtils.setField(faceManagementService, "collectionName", "face_embeddings");
        ReflectionTestUtils.setField(faceManagementService, "videoBucketName", "videos");
        ReflectionTestUtils.setField(faceManagementService, "peopleBucketName", "people");
        ReflectionTestUtils.setField(faceManagementService, "peopleCollectionName", "people_embeddings");
        ReflectionTestUtils.setField(faceManagementService, "alertCollectionName", "alert_embeddings");

        lenient().when(milvusClient.insert(any(InsertParam.class)))
                .thenReturn(R.success(MutationResult.newBuilder().build()));
        lenient().when(milvusClient.delete(any(DeleteParam.class)))
                .thenReturn(R.success(MutationResult.newBuilder().build()));
    }

    @Test
    void createPersonFromUpload_suspectFalse_writesTwoBucketsAndTwoVectorInserts() throws Exception {
        byte[] img = new byte[]{1, 2, 3, 4};
        when(faceSearchService.extractFeatureFromBytes(img, ".jpg")).thenReturn(embedding512());

        PeopleRecordDTO dto = faceManagementService.createPersonFromUpload(img, false);

        assertNotNull(dto.getPeopleId());
        assertNotNull(dto.getRepresentativeFaceId());
        verify(minioClient, times(2)).putObject(any());
        verify(milvusClient, times(2)).insert(any(InsertParam.class));
    }

    @Test
    void createPersonFromUpload_suspectTrue_writesAlertEmbedding() throws Exception {
        byte[] img = new byte[]{9, 8, 7};
        when(faceSearchService.extractFeatureFromBytes(img, ".jpg")).thenReturn(embedding512());

        faceManagementService.createPersonFromUpload(img, true);

        verify(minioClient, times(2)).putObject(any());
        verify(milvusClient, times(3)).insert(any(InsertParam.class));
    }

    @Test
    void deletePerson_removesPeopleObjectAndVectorRows() throws Exception {
        String peopleId = "p-del-1";
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());

        faceManagementService.deletePerson(peopleId);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        ArgumentCaptor<DeleteParam> delCap = ArgumentCaptor.forClass(DeleteParam.class);
        verify(milvusClient, atLeastOnce()).delete(delCap.capture());
        long peopleDeletes = delCap.getAllValues().stream()
                .map(DeleteParam::getCollectionName)
                .filter(n -> n.equals("people_embeddings") || n.equals("alert_embeddings"))
                .count();
        assertEquals(2, peopleDeletes);
    }

    @Test
    void mergePeople_sameSourceAndTarget_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> faceManagementService.mergePeople("same", "same"));
    }

    @Test
    void mergePeople_blankIds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> faceManagementService.mergePeople("", "b"));
        assertThrows(IllegalArgumentException.class,
                () -> faceManagementService.mergePeople("a", ""));
    }

    private static List<Float> embedding512() {
        List<Float> v = new java.util.ArrayList<>(512);
        for (int i = 0; i < 512; i++) {
            v.add(0.02f);
        }
        return v;
    }
}
