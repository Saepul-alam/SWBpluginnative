# Contributing

This guide provides instructions for contributing to this Capacitor plugin.

## Developing

### Local Setup

1. Fork and clone the repo.
2. Install dependencies:
   ```shell
   npm install
   ```

### Scripts

#### `npm run build`
Compiles TypeScript assets, generates docs with docgen, and bundles with Rollup.

#### `npm test`
Runs pure logic unit tests using Node's test runner.

#### `npm run lint` / `npm run fmt`
Checks and formats code using ESLint and Prettier.

#### Android Verification
```shell
cd android && ./gradlew lint test compileDebugJavaWithJavac --no-daemon
```

#### iOS Verification
```shell
cd ios && xcodebuild test -workspace Plugin.xcworkspace -scheme Plugin -sdk iphonesimulator -destination "platform=iOS Simulator,name=iPhone 16" CODE_SIGNING_ALLOWED=NO
```

## Pull Request Guidelines
1. Ensure all tests (`npm test`, Android gradlew, iOS xcodebuild) pass locally.
2. Run `npm run lint` and `npm run prettier -- --check`.
3. Adhere to SemVer conventions.
4. Follow the Code of Conduct.
