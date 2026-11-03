#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(SwbNotifPlugin, "SwbNotif",
           CAP_PLUGIN_METHOD(checkPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(requestPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(checkExactAlarmPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(requestExactAlarmPermission, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(createChannel, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(deleteChannel, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(listChannels, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(schedule, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(scheduleMultiple, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getPending, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(cancel, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(cancelAll, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getDelivered, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeDelivered, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(removeAllDelivered, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setBadgeCount, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getBadgeCount, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(createActionGroup, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(deleteActionGroup, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getLastAction, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(clearLastAction, CAPPluginReturnPromise);
)
