<template>
  <div id="app" class="container-fluid">
    <div v-if="is_logged">
      <admin-layout/>
      
    </div>
    <div v-else >
      <login-screen />
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import LoginScreen from './components/LoginScreen'
import AdminLayout from './components/AdminLayout'
export default {
  name: 'app',
  components: {
    LoginScreen,
    AdminLayout
  },
  data: function() {
    return { 
      rememberme:false 
    }
  },
  computed: mapState({
      is_logged: state =>  { console.log('is_logged',state); return (state.username !=null && state.password !=null && state.server !=null) }
  }),  
}
</script>

<style>
#app {
  font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,"Noto Sans",sans-serif,"Apple Color Emoji","Segoe UI Emoji","Segoe UI Symbol","Noto Color Emoji";
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  
  color: #2c3e50;
  margin-top: 60px;
}

.feather {
  width: 16px;
  height: 16px;
  vertical-align: text-bottom;
}
</style>
