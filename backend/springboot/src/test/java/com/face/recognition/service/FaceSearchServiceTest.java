package com.face.recognition.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.IDs;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.StringArray;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FaceSearchService: feature extraction failure, empty Milvus hits, threshold filtering on Milvus results.
 */
@ExtendWith(MockitoExtension.class)
class FaceSearchServiceTest {

    @Mock
    private MilvusServiceClient milvusClient;

    @Mock
    private MinioClient minioClient;

    @Mock
    private SettingsService settingsService;

    private FaceSearchService faceSearchService;

    @BeforeEach
    void setUp() {
        faceSearchService = new FaceSearchService();
        ReflectionTestUtils.setField(faceSearchService, "milvusClient", milvusClient);
        ReflectionTestUtils.setField(faceSearchService, "minioClient", minioClient);
        ReflectionTestUtils.setField(faceSearchService, "settingsService", settingsService);
        ReflectionTestUtils.setField(faceSearchService, "collectionName", "face_embeddings");
        ReflectionTestUtils.setField(faceSearchService, "bucketName", "faces");
        ReflectionTestUtils.setField(faceSearchService, "peopleBucketName", "people");
        ReflectionTestUtils.setField(faceSearchService, "videoBucketName", "videos");
        ReflectionTestUtils.setField(faceSearchService, "searchThreshold", 0.70f);
    }

    @Test
    void extractFeatureWithPython_nonZeroExitCode_throwsRuntimeException() throws Exception {
        Path temp = Files.createTempFile("feat_fail_", ".jpg");
        Files.write(temp, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        try (MockedConstruction<java.lang.ProcessBuilder> ignored = mockConstruction(java.lang.ProcessBuilder.class,
                (mock, context) -> {
                    java.lang.Process proc = mock(java.lang.Process.class);
                    when(mock.redirectErrorStream(true)).thenReturn(mock);
                    when(mock.start()).thenReturn(proc);
                    when(proc.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("stderr".getBytes()));
                    when(proc.waitFor()).thenReturn(1);
                })) {
            Method m = FaceSearchService.class.getDeclaredMethod("extractFeatureWithPython", String.class);
            m.setAccessible(true);
            InvocationTargetException ite = assertThrows(InvocationTargetException.class,
                    () -> m.invoke(faceSearchService, temp.toAbsolutePath().toString()));
            assertTrue(ite.getCause() instanceof RuntimeException);
            assertTrue(ite.getCause().getMessage().contains("Feature extraction failed"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void searchInMilvus_emptyIdScores_returnsEmptyList() throws Exception {
        SearchResultData data = SearchResultData.newBuilder()
                .setNumQueries(1)
                .setTopK(0)
                .build();
        SearchResults sr = SearchResults.newBuilder().setResults(data).build();
        when(milvusClient.search(any(SearchParam.class))).thenReturn(R.success(sr));
        when(settingsService.getSearchThreshold()).thenReturn(0.70f);

        Method m = FaceSearchService.class.getDeclaredMethod("searchInMilvus", List.class, Integer.class);
        m.setAccessible(true);
        List<?> out = (List<?>) m.invoke(faceSearchService, List.of(0.1f, 0.2f), 10);
        assertTrue(out.isEmpty());
    }

    @Test
    void searchInMilvus_appliesSearchThreshold() throws Exception {
        SearchResultData data = SearchResultData.newBuilder()
                .setNumQueries(1)
                .setTopK(3)
                .setIds(IDs.newBuilder()
                        .setStrId(StringArray.newBuilder()
                                .addData("f1")
                                .addData("f2")
                                .addData("f3")
                                .build())
                        .build())
                .addScores(0.90f)
                .addScores(0.65f)
                .addScores(0.72f)
                .build();
        SearchResults sr = SearchResults.newBuilder().setResults(data).build();
        when(milvusClient.search(any(SearchParam.class))).thenReturn(R.success(sr));
        when(settingsService.getSearchThreshold()).thenReturn(0.70f);

        Method m = FaceSearchService.class.getDeclaredMethod("searchInMilvus", List.class, Integer.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<com.face.recognition.dto.SearchMatchDTO> out =
                (List<com.face.recognition.dto.SearchMatchDTO>) m.invoke(faceSearchService, embedding512(), 10);

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(d -> d.getScore() >= 0.70f));
        verify(milvusClient).search(any(SearchParam.class));
    }

    private static List<Float> embedding512() {
        List<Float> v = new ArrayList<>(512);
        for (int i = 0; i < 512; i++) {
            v.add(0.01f);
        }
        return v;
    }
}
