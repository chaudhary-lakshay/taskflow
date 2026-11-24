// jsdom has no TextEncoder (browsers and Node do); the password byte-length check needs it.
const { TextEncoder } = require('util')
Object.assign(globalThis, { TextEncoder })
