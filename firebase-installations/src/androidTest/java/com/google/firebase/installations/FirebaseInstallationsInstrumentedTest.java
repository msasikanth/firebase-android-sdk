// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.installations;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_APP_ID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_3;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_AUTH_TOKEN_4;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_CREATION_TIMESTAMP_2;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_FID_1;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_PROJECT_ID;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_REFRESH_TOKEN;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP;
import static com.google.firebase.installations.FisAndroidTestConstants.TEST_TOKEN_EXPIRATION_TIMESTAMP_2;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.local.PersistedFid;
import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.FirebaseInstallationServiceException;
import com.google.firebase.installations.remote.InstallationResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FirebaseInstallationsInstrumentedTest {
  private FirebaseApp firebaseApp;
  private ExecutorService executor;
  private PersistedFid persistedFid;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsOk;
  @Mock private FirebaseInstallationServiceClient backendClientReturnsError;
  @Mock private PersistedFid persistedFidReturnsError;
  @Mock private Utils mockUtils;
  @Mock private Clock mockClock;
  @Mock private PersistedFid mockPersistedFid;

  private static final PersistedFidEntry REGISTERED_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .setRegistrationStatus(PersistedFid.RegistrationStatus.REGISTERED)
          .build();

  private static final PersistedFidEntry EXPIRED_AUTH_TOKEN_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP_2)
          .setRegistrationStatus(PersistedFid.RegistrationStatus.REGISTERED)
          .build();

  private static final PersistedFidEntry UNREGISTERED_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken("")
          .setRefreshToken("")
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
          .setExpiresInSecs(0)
          .setRegistrationStatus(PersistedFid.RegistrationStatus.UNREGISTERED)
          .build();

  private static final PersistedFidEntry UPDATED_AUTH_TOKEN_FID_ENTRY =
      PersistedFidEntry.builder()
          .setFirebaseInstallationId(TEST_FID_1)
          .setAuthToken(TEST_AUTH_TOKEN_2)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setTokenCreationEpochInSecs(TEST_CREATION_TIMESTAMP_2)
          .setExpiresInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .setRegistrationStatus(PersistedFid.RegistrationStatus.REGISTERED)
          .build();

  @Before
  public void setUp() throws FirebaseInstallationServiceException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    executor = new ThreadPoolExecutor(0, 4, 10L, TimeUnit.SECONDS, new SynchronousQueue<>());
    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey("api_key")
                .build());
    persistedFid = new PersistedFid(firebaseApp);
    when(backendClientReturnsOk.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            InstallationResponse.builder()
                .setName("/projects/" + TEST_PROJECT_ID + "/installations/" + TEST_FID_1)
                .setRefreshToken(TEST_REFRESH_TOKEN)
                .setAuthToken(
                    InstallationTokenResult.builder()
                        .setToken(TEST_AUTH_TOKEN)
                        .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                        .build())
                .build());
    when(backendClientReturnsOk.generateAuthToken(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            InstallationTokenResult.builder()
                .setToken(TEST_AUTH_TOKEN_2)
                .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                .build());
    when(backendClientReturnsError.createFirebaseInstallation(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "SDK Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    when(persistedFidReturnsError.insertOrUpdatePersistedFidEntry(any())).thenReturn(false);
    when(persistedFidReturnsError.readPersistedFidEntryValue()).thenReturn(null);
    when(mockUtils.createRandomFid()).thenReturn(TEST_FID_1);
    when(mockClock.currentTimeMillis()).thenReturn(TEST_CREATION_TIMESTAMP_1);
  }

  @After
  public void cleanUp() throws Exception {
    persistedFid.clear();
  }

  @Test
  public void testGetId_PersistedFidOk_BackendOk() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task fails.")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_multipleCalls_sameFIDReturned() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    // No exception, means success.
    assertWithMessage("getId Task fails.")
        .that(Tasks.await(firebaseInstallations.getId()))
        .isNotEmpty();
    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);

    Tasks.await(firebaseInstallations.getId());

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration status doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTERED);
  }

  @Test
  public void testGetId_PersistedFidOk_BackendError() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsError, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getId());

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(entryValue.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);

    // Waiting for Task that registers FID on the FIS Servers
    executor.awaitTermination(500, TimeUnit.MILLISECONDS);

    PersistedFidEntry updatedFidEntry = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Fid doesn't match")
        .that(updatedFidEntry.getFirebaseInstallationId())
        .isEqualTo(TEST_FID_1);
    assertWithMessage("Registration Fid doesn't match")
        .that(updatedFidEntry.getRegistrationStatus())
        .isEqualTo(PersistedFid.RegistrationStatus.REGISTER_ERROR);
  }

  @Test
  public void testGetId_PersistedFidError_BackendOk() throws InterruptedException {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsOk,
            persistedFidReturnsError,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getId());
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertWithMessage("Exception class doesn't match")
          .that(cause)
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.CLIENT_ERROR);
    }
  }

  @Test
  public void testGetAuthToken_fidDoesNotExist_successful() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, persistedFid, mockUtils);

    Tasks.await(firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    PersistedFidEntry entryValue = persistedFid.readPersistedFidEntryValue();
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(entryValue.getAuthToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_PersistedFidError_failure() throws Exception {
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsOk,
            persistedFidReturnsError,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(
          firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertWithMessage("Exception class doesn't match")
          .that(cause)
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  @Test
  public void testGetAuthToken_fidExists_successful() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_expiredAuthToken_fetchedNewTokenFromFIS() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(EXPIRED_AUTH_TOKEN_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
  }

  @Test
  public void testGetAuthToken_unregisteredFid_fetchedNewTokenFromFIS() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue())
        .thenReturn(UNREGISTERED_FID_ENTRY, REGISTERED_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    InstallationTokenResult installationTokenResult =
        Tasks.await(
            firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(installationTokenResult.getToken())
        .isEqualTo(TEST_AUTH_TOKEN);
  }

  @Test
  public void testGetAuthToken_serverError_failure() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    when(backendClientReturnsError.generateAuthToken(
            anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new FirebaseInstallationServiceException(
                "Server Error", FirebaseInstallationServiceException.Status.SERVER_ERROR));
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock,
            executor,
            firebaseApp,
            backendClientReturnsError,
            mockPersistedFid,
            mockUtils);

    // Expect exception
    try {
      Tasks.await(firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH));
      fail();
    } catch (ExecutionException expected) {
      Throwable cause = expected.getCause();
      assertWithMessage("Exception class doesn't match")
          .that(cause)
          .isInstanceOf(FirebaseInstallationsException.class);
      assertWithMessage("Exception status doesn't match")
          .that(((FirebaseInstallationsException) cause).getStatus())
          .isEqualTo(FirebaseInstallationsException.Status.SDK_INTERNAL_ERROR);
    }
  }

  @Test
  public void testGetAuthToken_multipleCalls_fetchedNewTokenOnce() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue())
        .thenReturn(
            EXPIRED_AUTH_TOKEN_ENTRY,
            EXPIRED_AUTH_TOKEN_ENTRY,
            EXPIRED_AUTH_TOKEN_ENTRY,
            UPDATED_AUTH_TOKEN_FID_ENTRY);
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, backendClientReturnsOk, mockPersistedFid, mockUtils);

    // Call getAuthToken multiple times with DO_NOT_FORCE_REFRESH option
    Task<InstallationTokenResult> task1 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH);
    Task<InstallationTokenResult> task2 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.DO_NOT_FORCE_REFRESH);

    Tasks.await(Tasks.whenAllComplete(task1, task2));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_2);
    verify(backendClientReturnsOk, times(1))
        .generateAuthToken(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testGetAuthToken_multipleCallsForceRefresh_fetchedNewTokenTwice() throws Exception {
    when(mockPersistedFid.readPersistedFidEntryValue()).thenReturn(REGISTERED_FID_ENTRY);
    // Use a custom ServiceClient to mock the network calls ensuring task1 is not completed
    // before task2. Hence, we can test multiple calls to getAUthToken() and verify task2 waits for
    // task1 to complete.
    ServiceClient serviceClient = new ServiceClient();
    FirebaseInstallations firebaseInstallations =
        new FirebaseInstallations(
            mockClock, executor, firebaseApp, serviceClient, mockPersistedFid, mockUtils);

    // Call getAuthToken multiple times with FORCE_REFRESH option. Also, sleep for 500ms in between
    // the calls to ensure tasks are called in order.
    Task<InstallationTokenResult> task1 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH);
    Thread.sleep(500);
    Task<InstallationTokenResult> task2 =
        firebaseInstallations.getAuthToken(FirebaseInstallationsApi.FORCE_REFRESH);
    Tasks.await(Tasks.whenAllComplete(task1, task2));

    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task1.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_3);
    assertWithMessage("Persisted Auth Token doesn't match")
        .that(task2.getResult().getToken())
        .isEqualTo(TEST_AUTH_TOKEN_4);
  }

  class ServiceClient extends FirebaseInstallationServiceClient {

    private boolean secondCall;

    @Override
    @NonNull
    public InstallationTokenResult generateAuthToken(
        @NonNull String apiKey,
        @NonNull String fid,
        @NonNull String projectID,
        @NonNull String refreshToken)
        throws FirebaseInstallationServiceException {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Log.e("InterruptedException", e.getMessage());
      }

      if (!secondCall) {
        secondCall = true;
        return InstallationTokenResult.builder()
            .setToken(TEST_AUTH_TOKEN_3)
            .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
            .build();
      }

      return InstallationTokenResult.builder()
          .setToken(TEST_AUTH_TOKEN_4)
          .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .build();
    }
  }
}
