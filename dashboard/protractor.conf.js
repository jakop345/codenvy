'use strict';

var paths = {
  src: 'src',
  dist: 'dist',
  tmp: '.tmp',
  e2e: 'e2e'
};

// An example configuration file.
exports.config = {
  // The address of a running selenium server.
  //seleniumAddress: 'http://localhost:4444/wd/hub',
  //seleniumServerJar: deprecated, this should be set on node_modules/protractor/config.json

  // Capabilities to be passed to the webdriver instance.
  capabilities: {
    'browserName': 'chrome',
    'chromeOptions': {
      args: ['--lang=en',
        '--window-size=1350,900 ']
    }
  },

  baseUrl: 'http://localhost:3000',

  // Spec patterns are relative to the current working directory when
  // protractor is called.
  specs: [paths.e2e + '/**/*.js'],

  // This can be changed via command line as:
  // --params.login.user='valid-login' --params.login.password='valid-password'
  params: {
    login: {
      user: 'fake-login',
      password: 'fake-password'
    },
    fakeLogin: {
      user: 'fake-login',
      password: 'fake-password'
    },
    // --params.devs='user1:pass1 user2:pass2 user3:pass3'
    devs: ''
  },

  // Suite can be run via command line as:
  // --suite teams
  suites: {
    login: paths.e2e + '/login/*.js',
    teams: paths.e2e + '/teams/**/*.js'
  },

  // Options to be passed to Jasmine-node.
  jasmineNodeOpts: {
    showColors: true,
    defaultTimeoutInterval: 30000
  }
};
