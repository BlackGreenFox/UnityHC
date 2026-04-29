// HealthConnectManager.cs
//
// C# bridge to the UnityHC Android Library (com.bgf.unityhc.HealthConnectPlugin).
//
// Drop this file under your Unity project's `Assets/Plugins/UnityHC/`
// folder along with the built `UnityHC-release.aar` placed under
// `Assets/Plugins/Android/UnityHC.aar`.
//
// Optional: link TMP_Text references in the Inspector to get an automatic
// on-screen readout of init status, today's summary and the raw JSON
// callbacks. Requires the TextMeshPro package (Window → TextMeshPro →
// Import TMP Essentials).
//
// Usage:
//   1. Create an empty GameObject in your scene named exactly
//      "HealthConnectManager" (must match `gameObjectName` passed to
//      `Init`).
//   2. Attach this script to it.
//   3. (Optional) drag TMP_Text fields onto the StatusText / SummaryText /
//      RawJsonText slots and toggle AutoInitOnStart / AutoRequestPermissionsOnStart.
//   4. Subscribe to the public events from your UI code if you need raw JSON.
//
// Notes:
//   - All native methods are no-ops on non-Android platforms (Editor / iOS /
//     standalone) so you can keep the script in a cross-platform project
//     without `#if UNITY_ANDROID` guards everywhere.
//   - Callbacks from Java arrive on Unity's main thread via
//     `UnityPlayer.UnitySendMessage`, so you can update UI directly from
//     event handlers.

using System;
using System.Collections;
using TMPro;
using UnityEngine;

namespace BGF.UnityHC
{
    public class HealthConnectManager : MonoBehaviour
    {
        // ---------- singleton ----------

        public static HealthConnectManager Instance { get; private set; }

        [Header("Bridge settings")]
        [Tooltip("Must match the GameObject name passed to Init().")]
        public string gameObjectName = "HealthConnectManager";

        [Tooltip("Call Init() automatically in Start().")]
        public bool autoInitOnStart = true;

        [Tooltip("Call RequestPermissions() automatically once Init() succeeds. " +
                 "Set false if you want to gate the permission prompt behind a UI button.")]
        public bool autoRequestPermissionsOnStart = true;

        [Tooltip("Once permissions are granted, automatically start polling " +
                 "today's summary at the interval below.")]
        public bool autoStartTrackingAfterGrant = false;

        [Tooltip("Polling interval for StartTracking() in milliseconds.")]
        public long trackingIntervalMillis = 3000L;

        [Header("UI (optional, TextMeshPro)")]
        [Tooltip("One-line status (Init / permissions / errors).")]
        public TMP_Text statusText;

        [Tooltip("Multi-line readout of today's summary (steps, distance, " +
                 "calories, heart rate).")]
        public TMP_Text summaryText;

        [Tooltip("Last raw JSON received from any callback. Useful for debugging.")]
        public TMP_Text rawJsonText;

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

        // ---------- lifecycle ----------

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

            // Wire UI updaters to our own events so the Inspector-linked
            // TMP_Text fields stay in sync with no extra code.
            OnInit             += HandleInitForUI;
            OnPermissions      += HandlePermissionsForUI;
            OnHasPermissions   += HandlePermissionsForUI;
            OnSummary          += HandleSummaryForUI;
            OnRecords          += HandleRawForUI;
            OnInsert           += HandleRawForUI;
        }

        IEnumerator Start()
        {
            SetStatus("Idle");
            if (autoInitOnStart)
            {
                // Wait one frame so other scripts that subscribe in their
                // own Start() don't miss the first OnInit event.
                yield return null;
                SetStatus("Initialising Health Connect…");
                Init();
            }
        }

        // ---------- public API ----------

        public void Init()
        {
            SafeCall(p => p.CallStatic("initFromUnity", gameObject.name),
                     OnInit, "initFromUnity");
        }

        public int GetSdkStatus()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try { return Plugin.CallStatic<int>("getSdkStatus"); }
            catch (Exception e) { Debug.LogException(e); return 1; }
#else
            return 1; // SDK_UNAVAILABLE
#endif
        }

        public void OpenHealthConnectInPlayStore()
        {
            SafeCall(p => p.CallStatic("openHealthConnectInPlayStore"), null,
                     "openHealthConnectInPlayStore");
        }

        public void RequestPermissions()
        {
            SafeCall(p => p.CallStatic("requestPermissions"),
                     OnPermissions, "requestPermissions");
        }

        public void HasAllPermissions()
        {
            SafeCall(p => p.CallStatic("hasAllPermissions"),
                     OnHasPermissions, "hasAllPermissions");
        }

        public void GetTodaySummary()
        {
            SafeCall(p => p.CallStatic("getTodaySummary"),
                     OnSummary, "getTodaySummary");
        }

        public void StartTracking(long intervalMillis = 3000L)
        {
            SafeCall(p => p.CallStatic("startTracking", intervalMillis),
                     OnSummary, "startTracking");
        }

        public void StopTracking()
        {
            SafeCall(p => p.CallStatic("stopTracking"), null, "stopTracking");
        }

        /// <param name="recordType">"Steps", "HeartRate", "Distance",
        /// "ActiveCaloriesBurned", "TotalCaloriesBurned" (case-insensitive).</param>
        public void GetRecords(string recordType, long startMillis, long endMillis)
        {
            SafeCall(p => p.CallStatic("getRecords", recordType, startMillis, endMillis),
                     OnRecords, "getRecords");
        }

        public void InsertSteps(long count, long startMillis, long endMillis)
        {
            SafeCall(p => p.CallStatic("insertSteps", count, startMillis, endMillis),
                     OnInsert, "insertSteps");
        }

        public void InsertHeartRate(long bpm, long timestampMillis)
        {
            SafeCall(p => p.CallStatic("insertHeartRate", bpm, timestampMillis),
                     OnInsert, "insertHeartRate");
        }

        public void InsertDistance(double meters, long startMillis, long endMillis)
        {
            SafeCall(p => p.CallStatic("insertDistance", meters, startMillis, endMillis),
                     OnInsert, "insertDistance");
        }

        public void InsertActiveCalories(double kcal, long startMillis, long endMillis)
        {
            SafeCall(p => p.CallStatic("insertActiveCalories", kcal, startMillis, endMillis),
                     OnInsert, "insertActiveCalories");
        }

        public void InsertTotalCalories(double kcal, long startMillis, long endMillis)
        {
            SafeCall(p => p.CallStatic("insertTotalCalories", kcal, startMillis, endMillis),
                     OnInsert, "insertTotalCalories");
        }

        // Wraps every JNI call in a try/catch so a missing AAR or a Java
        // exception is surfaced to the user instead of silently leaving the
        // status text in "Initialising…". AndroidJavaClass is part of
        // UnityEngine and compiles on every platform; the body simply isn't
        // executed off-device.
        void SafeCall(Action<AndroidJavaClass> body, Action<string> errorChannel, string label)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                body(Plugin);
            }
            catch (AndroidJavaException eJava)
            {
                ReportNativeError(label, eJava, errorChannel);
            }
            catch (Exception e)
            {
                ReportNativeError(label, e, errorChannel);
            }
#else
            errorChannel?.Invoke("{\"ok\":false,\"error\":\"Not on Android (" + label + ")\"}");
#endif
        }

        void ReportNativeError(string label, Exception e, Action<string> errorChannel)
        {
            string msg = e.Message ?? e.GetType().Name;
            // Strip newlines so the JSON stays single-line for TMP_Text.
            msg = msg.Replace('\n', ' ').Replace('\r', ' ').Replace("\"", "'");
            string json = "{\"ok\":false,\"error\":\"" + label + ": " + msg + "\"}";
            Debug.LogError("[HealthConnect] " + label + " failed: " + e);
            SetStatus("Native error in " + label + ": " + msg);
            errorChannel?.Invoke(json);
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

        // ---------- UI plumbing ----------

        void HandleInitForUI(string json)
        {
            SetRaw(json);
            var r = JsonUtility.FromJson<InitResult>(json);
            if (r != null && r.ok)
            {
                SetStatus($"Health Connect ready (status={r.status})");
                if (autoRequestPermissionsOnStart) RequestPermissions();
            }
            else
            {
                SetStatus("Init failed: " + (r != null ? r.error : "unknown"));
            }
        }

        void HandlePermissionsForUI(string json)
        {
            SetRaw(json);
            var r = JsonUtility.FromJson<PermissionResult>(json);
            if (r != null && r.ok && r.all)
            {
                SetStatus("Permissions granted");
                GetTodaySummary();
                if (autoStartTrackingAfterGrant) StartTracking(trackingIntervalMillis);
            }
            else if (r != null && r.ok)
            {
                SetStatus("Permissions partially granted (missing some)");
            }
            else
            {
                SetStatus("Permissions error: " + (r != null ? r.error : "unknown"));
            }
        }

        void HandleSummaryForUI(string json)
        {
            SetRaw(json);
            var s = JsonUtility.FromJson<SummaryResult>(json);
            if (s == null || !s.ok)
            {
                SetSummary("Summary error: " + (s != null ? s.error : "no payload"));
                return;
            }
            SetSummary(
                $"Steps:    {s.steps}\n" +
                $"Distance: {s.distanceMeters:F0} m\n" +
                $"Active:   {s.activeKcal:F1} kcal\n" +
                $"Total:    {s.totalKcal:F1} kcal\n" +
                $"HR avg:   {FormatHr(s.hrAvg)} bpm\n" +
                $"HR min:   {FormatHr(s.hrMin)} bpm\n" +
                $"HR max:   {FormatHr(s.hrMax)} bpm");
        }

        void HandleRawForUI(string json) => SetRaw(json);

        void SetStatus(string s)
        {
            if (statusText != null) statusText.text = s;
            Debug.Log("[HealthConnect] " + s);
        }

        void SetSummary(string s)
        {
            if (summaryText != null) summaryText.text = s;
        }

        void SetRaw(string s)
        {
            if (rawJsonText != null) rawJsonText.text = s;
        }

        static string FormatHr(double v) => v <= 0 ? "—" : v.ToString("F0");

        // ---------- DTOs (JsonUtility-friendly) ----------

        [Serializable]
        class InitResult
        {
            public bool ok;
            public int status;
            public string error;
        }

        [Serializable]
        class PermissionResult
        {
            public bool ok;
            public bool all;
            public string error;
            // granted/missing arrays exist in the JSON but aren't surfaced
            // to UI here — read them from OnPermissions if you need them.
        }

        [Serializable]
        class SummaryResult
        {
            public bool ok;
            public string error;
            public long steps;
            public double distanceMeters;
            public double activeKcal;
            public double totalKcal;
            public double hrAvg;
            public double hrMin;
            public double hrMax;
        }
    }
}
