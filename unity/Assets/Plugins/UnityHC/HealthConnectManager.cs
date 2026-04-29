// HealthConnectManager.cs
//
// C# bridge to the UnityHC Android Library (com.bgf.unityhc.HealthConnectPlugin).
//
// Drop this file under your Unity project's `Assets/Plugins/UnityHC/`
// folder along with the built `UnityHC-release.aar` placed under
// `Assets/Plugins/Android/UnityHC.aar`.
//
// Usage:
//   1. Create an empty GameObject in your scene named exactly
//      "HealthConnectManager" (must match `gameObjectName` passed to
//      `Init`).
//   2. Attach this script to it.
//   3. Subscribe to the public events from your UI code, then call
//      `HealthConnectManager.Instance.Init()`.
//
// Notes:
//   - All native methods are no-ops on non-Android platforms (Editor / iOS /
//     standalone) so you can safely keep the script in a cross-platform
//     project without `#if UNITY_ANDROID` guards everywhere.
//   - Callbacks from Java arrive on Unity's main thread via
//     `UnityPlayer.UnitySendMessage`, so you can update UI directly from
//     event handlers.

using System;
using UnityEngine;

namespace BGF.UnityHC
{
    public class HealthConnectManager : MonoBehaviour
    {
        // ---------- singleton ----------

        public static HealthConnectManager Instance { get; private set; }

        [Tooltip("Must match the GameObject name passed to Init().")]
        public string gameObjectName = "HealthConnectManager";

        // ---------- events (raw JSON) ----------

        /// <summary>Fired after Init(): {"ok":true,"status":N} or {"ok":false,...}.</summary>
        public event Action<string> OnInit;

        /// <summary>{"ok":true,"all":bool,"granted":[...],"missing":[...]} after RequestPermissions().</summary>
        public event Action<string> OnPermissions;

        /// <summary>{"ok":true,"all":bool,"granted":[...],"missing":[...]} after HasAllPermissions().</summary>
        public event Action<string> OnHasPermissions;

        /// <summary>{"ok":true, steps, distanceMeters, activeKcal, totalKcal, hrAvg, hrMin, hrMax, ...}.</summary>
        public event Action<string> OnSummary;

        /// <summary>{"ok":true, recordType, count, records:[...]} after GetRecords().</summary>
        public event Action<string> OnRecords;

        /// <summary>{"ok":true, ids:[...]} after Insert*().</summary>
        public event Action<string> OnInsert;

        // ---------- native bridge ----------

        const string PluginClass = "com.bgf.unityhc.HealthConnectPlugin";

#if UNITY_ANDROID && !UNITY_EDITOR
        AndroidJavaClass _plugin;
        AndroidJavaClass Plugin => _plugin ??= new AndroidJavaClass(PluginClass);
#endif

        void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            if (!string.IsNullOrEmpty(gameObjectName))
                gameObject.name = gameObjectName;
        }

        // ---------- public API ----------

        public void Init()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("initFromUnity", gameObject.name);
#else
            OnInit?.Invoke("{\"ok\":false,\"error\":\"Not on Android\"}");
#endif
        }

        public int GetSdkStatus()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            return Plugin.CallStatic<int>("getSdkStatus");
#else
            return 1; // SDK_UNAVAILABLE
#endif
        }

        public void OpenHealthConnectInPlayStore()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("openHealthConnectInPlayStore");
#endif
        }

        public void RequestPermissions()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("requestPermissions");
#else
            OnPermissions?.Invoke("{\"ok\":false,\"error\":\"Not on Android\"}");
#endif
        }

        public void HasAllPermissions()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("hasAllPermissions");
#else
            OnHasPermissions?.Invoke("{\"ok\":false,\"error\":\"Not on Android\"}");
#endif
        }

        public void GetTodaySummary()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("getTodaySummary");
#else
            OnSummary?.Invoke("{\"ok\":false,\"error\":\"Not on Android\"}");
#endif
        }

        public void StartTracking(long intervalMillis = 3000L)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("startTracking", intervalMillis);
#endif
        }

        public void StopTracking()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("stopTracking");
#endif
        }

        /// <param name="recordType">"Steps", "HeartRate", "Distance",
        /// "ActiveCaloriesBurned", "TotalCaloriesBurned" (case-insensitive).</param>
        public void GetRecords(string recordType, long startMillis, long endMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("getRecords", recordType, startMillis, endMillis);
#else
            OnRecords?.Invoke("{\"ok\":false,\"error\":\"Not on Android\"}");
#endif
        }

        public void InsertSteps(long count, long startMillis, long endMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("insertSteps", count, startMillis, endMillis);
#endif
        }

        public void InsertHeartRate(long bpm, long timestampMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("insertHeartRate", bpm, timestampMillis);
#endif
        }

        public void InsertDistance(double meters, long startMillis, long endMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("insertDistance", meters, startMillis, endMillis);
#endif
        }

        public void InsertActiveCalories(double kcal, long startMillis, long endMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("insertActiveCalories", kcal, startMillis, endMillis);
#endif
        }

        public void InsertTotalCalories(double kcal, long startMillis, long endMillis)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Plugin.CallStatic("insertTotalCalories", kcal, startMillis, endMillis);
#endif
        }

        // ---------- callbacks (invoked by UnityPlayer.UnitySendMessage) ----------
        // Method names MUST match HealthConnectPlugin.CB_* constants.

        // ReSharper disable UnusedMember.Local
        void OnHealthConnectInit(string json)     => OnInit?.Invoke(json);
        void OnPermissionsResult(string json)     => OnPermissions?.Invoke(json);
        void OnHasPermissionsResult(string json)  => OnHasPermissions?.Invoke(json);
        void OnHealthSummaryReceived(string json) => OnSummary?.Invoke(json);
        void OnRecordsReceived(string json)       => OnRecords?.Invoke(json);
        void OnInsertResult(string json)          => OnInsert?.Invoke(json);
        // ReSharper restore UnusedMember.Local
    }
}
