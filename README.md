# UnityHC — Health Connect Unity AAR Plugin

Android Library (`.aar`) that exposes [Android Health Connect](https://developer.android.com/health-and-fitness/health-connect) (steps, heart-rate, distance, active/total calories) to a Unity 6 project, with a thin C# wrapper.

```
UnityHC/                  # Android Library module → produces UnityHC-release.aar
app/                      # Tiny host APK used to test the library on a device
unity/Assets/Plugins/UnityHC/HealthConnectManager.cs   # C# bridge to drop into Unity
```

## Requirements

| | |
|---|---|
| Unity | 6.0+ (`6000.0.x`). Android player. |
| Android API | `minSdk = 33`, `compileSdk = 36`. |
| JDK | 17. |
| Health Connect provider | `com.google.android.apps.healthdata` (pre-installed on Android 14+, otherwise installed from Play Store). |
| AGP / Gradle | AGP `9.2.0`, Gradle `9.4.1` (matches the wrapper checked in here). |

## Building the AAR

```bash
./gradlew :UnityHC:assembleRelease
# Output:
#   UnityHC/build/outputs/aar/UnityHC-release.aar
```

## Adding the AAR to a Unity 6 project

1. Copy `UnityHC-release.aar` into your Unity project at
   `Assets/Plugins/Android/UnityHC.aar`. In the Inspector, make sure the
   import settings target **Android** only.
2. Copy [`unity/Assets/Plugins/UnityHC/HealthConnectManager.cs`](unity/Assets/Plugins/UnityHC/HealthConnectManager.cs) into the same folder of your Unity project (`Assets/Plugins/UnityHC/`).
3. In Unity → **Project Settings → Player → Android → Other Settings**:
   - **Minimum API Level:** Android 13 (API 33) or higher.
   - **Target API Level:** Android 14 (API 34) or higher.
   - **Scripting Backend:** IL2CPP (recommended).
   - Make sure **Custom Main Manifest** is *off* — the Health Connect
     `<uses-permission>`s, `<queries>` and the `PermissionsRationaleActivity`
     are merged in automatically from the AAR's `AndroidManifest.xml`.
4. Create an empty GameObject in your bootstrap scene, name it
   exactly `HealthConnectManager` and attach `HealthConnectManager.cs`.
   The name is what `UnityPlayer.UnitySendMessage` uses to deliver
   callbacks back to C#.
5. **Tell Unity about the AAR's transitive dependencies.** Unity does
   *not* read the dependencies declared inside an `.aar` POM, so without
   this step you will see at runtime:
   ```
   java.lang.NoClassDefFoundError: Failed resolution of:
       Landroidx/health/connect/client/permission/HealthPermission;
   ```
   Pick **one** of the two options:

   **Option A — Custom Main Gradle Template (no extra packages)**
   1. Player Settings → Android → Publishing Settings → Build → tick
      **Custom Main Gradle Template**.
   2. Open the generated `Assets/Plugins/Android/mainTemplate.gradle`.
   3. Inside the `dependencies { … }` block (just before the closing
      `}`), add:
      ```gradle
      implementation 'androidx.health.connect:connect-client:1.1.0'
      implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
      ```
   A ready-made template is included in this repo at
   [`unity/Assets/Plugins/Android/mainTemplate.gradle`](unity/Assets/Plugins/Android/mainTemplate.gradle)
   if you'd rather copy it as-is.

   **Option B — External Dependency Manager for Unity (EDM4U)**
   1. Install EDM4U from
      <https://github.com/googlesamples/unity-jar-resolver/releases>
      (a `.unitypackage`).
   2. Drop
      [`unity/Assets/Plugins/UnityHC/Editor/UnityHCDependencies.xml`](unity/Assets/Plugins/UnityHC/Editor/UnityHCDependencies.xml)
      into the same path of your Unity project.
   3. **Assets → External Dependency Manager → Android Resolver →
      Force Resolve.**
   EDM4U will fetch the Health Connect client and the Kotlin coroutines
   runtime from Google's Maven / Maven Central and pack them into the
   APK on every Android build.

6. *(Optional, recommended for testing)* In Unity → **Window → TextMeshPro
   → Import TMP Essentials**, then build a Canvas with four
   `TextMeshProUGUI` components and drag them onto the
   **Status Text / Summary Text / Raw JSON Text / Log Text** slots of
   `HealthConnectManager`. With **Auto Init On Start** and
   **Auto Request Permissions On Start** enabled (default), the manager
   will request Health Connect permissions on app launch and render the
   summary on screen automatically — no extra C# code required. The
   **Log Text** slot mirrors every Java log line plus every Unity
   `Debug.Log*` call, so you can debug on a device without `adb`.

## Auto-start behaviour

`HealthConnectManager` exposes four toggles in the Inspector:

| Toggle | Default | What it does |
|---|---|---|
| `Auto Init On Start`                  | **true** | Calls `Init()` one frame after `Start()`. |
| `Auto Request Permissions On Start`   | **true** | After Init succeeds, immediately calls `RequestPermissions()` to show the Health Connect permission dialog. |
| `Auto Start Tracking After Grant`     | false    | After all permissions are granted, calls `StartTracking(intervalMillis)` so `OnSummary` fires periodically. |
| `Tracking Interval Millis`            | 3000     | Polling interval used by `StartTracking()`. |

If you want full manual control, untick the auto toggles and call
`HealthConnectManager.Instance.Init()` / `RequestPermissions()` yourself
from a button.

## Using the API from C#

```csharp
using BGF.UnityHC;
using UnityEngine;

public class Demo : MonoBehaviour
{
    void Start()
    {
        var hc = HealthConnectManager.Instance;

        hc.OnInit        += json => Debug.Log("init: " + json);
        hc.OnPermissions += json => Debug.Log("perm: " + json);
        hc.OnSummary     += json => Debug.Log("today: " + json);
        hc.OnInsert      += json => Debug.Log("insert: " + json);

        hc.Init();                      // resolves UnityPlayer.currentActivity
        hc.RequestPermissions();        // shows Health Connect permission UI
        hc.GetTodaySummary();           // one-off pull
        hc.StartTracking(3000);         // OnSummary every 3s

        // Manual entry: 100 steps in the last minute
        long now   = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        long start = now - 60_000;
        hc.InsertSteps(100, start, now);
    }
}
```

### Reading raw records

```csharp
long now    = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
long dayAgo = now - 24L * 60 * 60 * 1000;

hc.OnRecords += json => Debug.Log(json);
hc.GetRecords("HeartRate", dayAgo, now);
hc.GetRecords("Steps",     dayAgo, now);
hc.GetRecords("Distance",  dayAgo, now);
hc.GetRecords("ActiveCaloriesBurned", dayAgo, now);
hc.GetRecords("TotalCaloriesBurned",  dayAgo, now);
```

## Permissions

The library declares all 10 Health Connect permissions (read+write × 5 record types) in its own `AndroidManifest.xml`, so they are merged into the host app automatically. The first call to `RequestPermissions()` opens Health Connect's permission UI; the result comes back via `OnPermissions` as JSON `{"ok":true,"all":bool,"granted":[…],"missing":[…]}`.

To check current state without re-prompting use `HasAllPermissions()` → `OnHasPermissions`.

## Privacy policy (required by Google Play)

`PermissionsRationaleActivity` is registered for `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`. It contains placeholder text — **before publishing the app to Play Store, replace the activity body with your real privacy policy** (or override the activity with the same name + intent-filter in the Unity host project, which will take precedence over the merged manifest).

## Callbacks reference

| GameObject method | Triggered by | Payload |
|---|---|---|
| `OnHealthConnectInit`     | `Init()`                  | `{ok,status}` |
| `OnPermissionsResult`     | `RequestPermissions()`    | `{ok,all,granted,missing}` |
| `OnHasPermissionsResult`  | `HasAllPermissions()`     | `{ok,all,granted,missing}` |
| `OnHealthSummaryReceived` | `GetTodaySummary()`, `StartTracking()` | `{ok,steps,distanceMeters,activeKcal,totalKcal,hrAvg,hrMin,hrMax,...}` |
| `OnRecordsReceived`       | `GetRecords(type,start,end)` | `{ok,recordType,count,records:[...]}` |
| `OnInsertResult`          | `Insert*()`               | `{ok,ids:[...]}` |

All payloads are JSON. Errors look like `{"ok":false,"error":"..."}`.

## Troubleshooting

### `NoClassDefFoundError: …/HealthPermission` at runtime

You forgot step 5 above. Unity packed the `.aar` but not its
transitive dependencies. Enable **Custom Main Gradle Template** (or use
EDM4U) and add the two `implementation` lines to your project's
`Assets/Plugins/Android/mainTemplate.gradle`.

### Status stuck at "Initialising Health Connect…"

The C# wrapper called `Plugin.CallStatic("initFromUnity", ...)` but never received `OnHealthConnectInit` back. Most common causes, in order:

1. **AAR not packaged into the APK.** Click `Assets/Plugins/Android/UnityHC.aar` in the Project window and verify in the Inspector:
   - "Select platforms for plugin" → only **Android** is ticked (not Editor / Standalone / Any Platform).
   - "Platform settings → CPU" → **ARMv7 / ARM64** (not "Editor").
   The wrapper now wraps every JNI call in `try/catch` and prints `Native error in initFromUnity: java.lang.ClassNotFoundException: com.bgf.unityhc.HealthConnectPlugin` straight into `Status Text` if this happens.
2. **Unity 6 reflection.** Unity 6.0+ moved `currentActivity` and `UnitySendMessage` from `com.unity3d.player.UnityPlayer` to `com.unity3d.player.UnityPlayerForActivityOrService`. The plugin already probes both classes and logs which one resolved (look for `initFromUnity: ... currentActivity='com.unity3d.player.UnityPlayerForActivityOrService'` in the on-screen log).
3. **Wrong GameObject name.** `UnityPlayer.UnitySendMessage` is name-based. The scene GameObject must be named exactly the string passed to `Init()` (default `HealthConnectManager`). Note the script's `Awake()` renames the GameObject to `gameObjectName` — so make sure that field hasn't been edited to something else.
4. **No Health Connect provider.** On a "naked" emulator without Google Play Services, `HealthConnectClient.getSdkStatus()` returns `SDK_UNAVAILABLE`. You'll see `OnInit` fire with `{"ok":false,"error":"Health Connect not available"}`. Use a real Android 13+ device or call `OpenHealthConnectInPlayStore()` to install the provider.

### Reading Android logcat

```
adb logcat -c
# launch the app
adb logcat -v time *:W Unity:V HealthConnectPlugin:V AndroidJavaException:V
```

The Kotlin side logs warnings under `HealthConnectPlugin`; Unity logs every `AndroidJavaException` and every `Debug.LogError` from this script.

### Other

* **`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`** → call `OpenHealthConnectInPlayStore()` to prompt the user to update.
* **Permissions silently denied on second open** → on Android 14+, repeatedly denying a permission disables the dialog. The user has to grant it manually in Settings → Apps → Health Connect → Permissions.
