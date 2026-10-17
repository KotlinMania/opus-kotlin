try {
    module.exports = require('./build/Release/opus_native.node');
} catch (err) {
    try {
        module.exports = require('./build/Debug/opus_native.node');
    } catch (err2) {
        throw new Error(
            'Could not load opus_native addon. ' +
            'Run `npm install` or `node-gyp rebuild` in native/node-opus directory.\n' +
            'Original error: ' + err.message + '\n' +
            'Debug error: ' + err2.message
        );
    }
}
