// HealthConnectButtonsExample.cs
//
// Drop-in example wiring four UI Buttons to the manual flow of
// HealthConnectManager. Use this if you'd rather drive Init / Request
// Permissions / Get Today Summary / Start Tracking from buttons in
// your scene instead of relying on the auto-init toggles.
//
// Setup:
//   1. Add four Buttons under your Canvas (e.g. via UI > Button -
//      TextMeshPro). Name them whatever you like.
//   2. Drop this script onto any GameObject in the scene (the same
//      one that has HealthConnectManager works).
//   3. Drag each Button into the matching slot in the Inspector.
//
// You can also skip this script entirely and wire each Button's
// OnClick directly to HealthConnectManager.Instance.Init() etc. via
// the Inspector — the methods on HealthConnectManager are public and
// take no arguments, so they show up as targets in the OnClick
// dropdown.

using UnityEngine;
using UnityEngine.UI;

namespace BGF.UnityHC
{
    public class HealthConnectButtonsExample : MonoBehaviour
    {
        [Header("UI Buttons (optional — leave any unset to skip)")]
        public Button initButton;
        public Button requestPermissionsButton;
        public Button hasPermissionsButton;
        public Button getTodaySummaryButton;
        public Button startTrackingButton;
        public Button stopTrackingButton;
        public Button openHealthConnectInPlayStoreButton;

        void Start()
        {
            Wire(initButton,                          () => HealthConnectManager.Instance?.Init());
            Wire(requestPermissionsButton,            () => HealthConnectManager.Instance?.RequestPermissions());
            Wire(hasPermissionsButton,                () => HealthConnectManager.Instance?.HasAllPermissions());
            Wire(getTodaySummaryButton,               () => HealthConnectManager.Instance?.GetTodaySummary());
            Wire(startTrackingButton,                 () => HealthConnectManager.Instance?.StartTracking());
            Wire(stopTrackingButton,                  () => HealthConnectManager.Instance?.StopTracking());
            Wire(openHealthConnectInPlayStoreButton,  () => HealthConnectManager.Instance?.OpenHealthConnectInPlayStore());
        }

        static void Wire(Button b, System.Action action)
        {
            if (b == null) return;
            b.onClick.AddListener(() => action?.Invoke());
        }
    }
}
