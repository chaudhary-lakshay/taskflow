/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'jsdom',
  setupFiles: ['<rootDir>/jest.polyfills.cjs'],
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.ts'],
  testMatch: ['<rootDir>/src/**/*.test.{ts,tsx}'],
  moduleNameMapper: { '\\.(css|svg|png)$': '<rootDir>/src/fileMock.ts' },
  // react-router and its dependencies ship ES modules only (some as .mjs); let babel-jest convert them.
  transform: { '\\.[cm]?[jt]sx?$': 'babel-jest' },
  transformIgnorePatterns: ['/node_modules/(?!(react-router|@remix-run|cookie-es)/)'],
}
