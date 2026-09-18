import Toybox.Application;
import Toybox.WatchUi;
import Toybox.Lang;

// Data-field app entry point.
// Returning [view, delegate] is what enables onTap() on touch-screen Edge units.
class ScoutApp extends Application.AppBase {

    hidden var _view as ScoutView?;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void { state = state; }
    function onStop(state as Dictionary?) as Void { state = state; }

    // SDK 6+ types this as a tuple; the old Array<Views or InputDelegates>?
    // form no longer type-checks.
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var view = new ScoutView();
        _view = view;
        return [view, new ScoutDelegate(view)];
    }

    // Garmin Connect / Express syncs data-field properties; there is no
    // on-device Scout app to open. Rebuild the grid after that sync.
    function onSettingsChanged() as Void {
        if (_view != null) {
            _view.reloadGrid();
            WatchUi.requestUpdate();
        }
    }
}
