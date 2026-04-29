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

* **`HealthConnectClient.SDK_UNAVAILABLE`** → device runs Android < 13 or has no Health Connect provider. Library is min-SDK 33 so this should only happen on emulators without the Health Connect APK.
* **`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`** → call `OpenHealthConnectInPlayStore()` to prompt the user to update.
* **No callback received** → check the GameObject name in the scene is exactly the string passed to `Init()` (default: `HealthConnectManager`). `UnitySendMessage` is name-based.
* **Permissions silently denied on second open** → on Android 14+, repeatedly denying a permission disables the dialog. The user has to grant it manually in Settings → Apps → Health Connect → Permissions.
