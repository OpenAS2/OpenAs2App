import Vue from 'vue'
import App from './App.vue'
import router from './routes'
import store from './store'
import VueHighcharts from 'vue-highcharts';
import Vuelidate from 'vuelidate'
import VeeValidate from "vee-validate";
import VueScreen from 'vue-screen';
import { BootstrapVue, IconsPlugin } from 'bootstrap-vue'
Vue.use(VeeValidate, {
  inject: true,
  fieldsBagName: "veeFields",
  errorBagName: "veeErrors"
});
// Import Bootstrap and BootstrapVue CSS files (order is important)
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue/dist/bootstrap-vue.css'
Vue.config.productionTip = false
Vue.use(Vuelidate)

Vue.use(VueHighcharts);
// Make BootstrapVue available throughout your project
Vue.use(BootstrapVue)
// Optionally install the BootstrapVue icon components plugin
Vue.use(IconsPlugin)
Vue.use(VueScreen, 'bootstrap');
new Vue({
  el:'#app',
  router,
  store,
  render: h => h(App)
});
