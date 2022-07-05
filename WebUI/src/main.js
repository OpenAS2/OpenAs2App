import Vue from 'vue'
import App from './App.vue'
import router from './routes'
import store from './store'
import vuetify from './plugins/vuetify'

Vue.config.productionTip = false


new Vue({
  el:'#app',
  router,
  store,
  vuetify,
  render: h => h(App)
});
