/* OmarchyTV Samsung Tizen shim: loaded before compat.js only in the Tizen .wgt (see build-wgt.sh).
 * Registers the remote keys the shared webOS app uses and maps Tizen app IDs for launchApp().
 * App IDs below are the commonly published Samsung store IDs; NOT verified on real hardware. */
(function () {
  if (!window.tizen) return;
  window.OMARCHY_PLATFORM = "tizen";
  window.OMARCHY_TIZEN_IDS = {
    youtube: "111299001912",
    netflix: "3201907018807",
    tv: "org.tizen.tv-viewer"
  };
  try {
    var keys = ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaStop", "MediaFastForward", "MediaRewind",
      "ChannelUp", "ChannelDown", "ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue",
      "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
    keys.forEach(function (k) { try { window.tizen.tvinputdevice.registerKey(k); } catch (e) {} });
  } catch (e) {}
})();

// Window title says Tizen instead of the shared webOS title.
document.addEventListener("DOMContentLoaded", function () { document.title = document.title.replace("(webOS)", "(Tizen)"); });
