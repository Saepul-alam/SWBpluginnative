import { SplashScreen } from '@capacitor/splash-screen';
import { SwbNotif } from 'swbnotif';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        display: block;
        width: 100%;
        height: 100%;
      }
      .button {
        display: inline-block;
        padding: 10px;
        margin: 4px 4px 4px 0;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 3px;
        cursor: pointer;
      }
      main { padding: 15px; }
      h1 { font-size: 1.4em; text-transform: uppercase; letter-spacing: 1px; }
      h2 { font-size: 1.1em; }
      pre { white-space: pre-wrap; background: #f4f4f4; padding: 8px; border-radius: 4px; }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>SwbNotif</h1>
      </capacitor-welcome-titlebar>
      <main>
        <h2>Notification demo</h2>
        <p>
          <button class="button" id="permission">Request permission</button>
          <button class="button" id="channel">Create channel</button>
        </p>
        <p>
          <button class="button" id="local">Local notification</button>
          <button class="button" id="scheduled">Schedule in 5s</button>
          <button class="button" id="actions">Notification with actions</button>
        </p>
        <p>
          <button class="button" id="pending">Get pending</button>
          <button class="button" id="cancel-all">Cancel all</button>
          <button class="button" id="push">Register push</button>
        </p>
        <pre id="log">Ready.</pre>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const root = this.shadowRoot;
      const log = message => {
        root.querySelector('#log').textContent =
          typeof message === 'string'
            ? message
            : JSON.stringify(message, null, 2);
      };

      root.querySelector('#permission').addEventListener('click', async () => {
        log(await SwbNotif.requestPermission());
      });

      root.querySelector('#channel').addEventListener('click', async () => {
        await SwbNotif.createChannel({
          id: 'demo',
          name: 'Demo',
          importance: 'high',
          vibration: true,
        });
        log('Channel "demo" created');
      });

      root.querySelector('#local').addEventListener('click', async () => {
        log(
          await SwbNotif.schedule({
            title: 'Hello from SwbNotif',
            body: 'Local notification',
            channelId: 'demo',
            extra: { route: '/inbox' },
          }),
        );
      });

      root.querySelector('#scheduled').addEventListener('click', async () => {
        log(
          await SwbNotif.schedule({
            title: 'Scheduled',
            body: 'This was scheduled 5 seconds ago',
            channelId: 'demo',
            scheduleAt: new Date(Date.now() + 5000),
          }),
        );
      });

      root.querySelector('#actions').addEventListener('click', async () => {
        await SwbNotif.createActionGroup({
          id: 'demo-actions',
          actions: [
            {
              id: 'reply',
              title: 'Reply',
              input: true,
              inputPlaceholder: 'Type a reply',
            },
            { id: 'dismiss', title: 'Dismiss', destructive: true },
          ],
        });
        log(
          await SwbNotif.schedule({
            title: 'Actionable',
            body: 'Try the action buttons',
            channelId: 'demo',
            actionGroupId: 'demo-actions',
          }),
        );
      });

      root.querySelector('#pending').addEventListener('click', async () => {
        log(await SwbNotif.getPending());
      });

      root.querySelector('#cancel-all').addEventListener('click', async () => {
        await SwbNotif.cancelAll();
        log('All notifications cancelled');
      });

      root.querySelector('#push').addEventListener('click', async () => {
        log(await SwbNotif.registerPush());
      });

      SwbNotif.addListener('notificationReceived', notification =>
        log({ event: 'received', notification }),
      );
      SwbNotif.addListener('notificationActionPerformed', event =>
        log({ event: 'action', ...event }),
      );

      SwbNotif.getLastAction().then(action => {
        if (action) {
          log({ event: 'launch action', ...action });
        }
      });
    }
  },
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  },
);
