<template>
<div id="loginscreen">
    <h1 v-if="loading" class="h3 mb-3 font-weight-normal">Signing in...</h1>
    <form v-else class="form-signin">

   <svg xmlns="http://www.w3.org/2000/svg" width="120" height="120">
       <text x="55" y="50" font-size="20" style="color:red">Open</text>
       <text x="5" y="95" font-size="55" font-weight="bolder">AS2</text>
   </svg> 
   
    
    <h1 class="h3 mb-3 font-weight-normal">Please sign in</h1>
    <label for="inputUsername" class="sr-only">Username</label>
    <input v-model="username" type="text" id="inputUsername" class="form-control" placeholder="Username" required="" autofocus="">
    <label for="inputPassword" class="sr-only">Password</label>
    <input v-model="password" type="password" id="inputPassword" class="form-control" placeholder="Password" required="">
    <label for="inputServer" class="sr-only">Server</label>
    <input v-model="server" type="text" id="inputServer" class="form-control" placeholder="Server" required="" autofocus="">
    <div class="checkbox mb-3">
        <label>
        <input type="checkbox" v-bind:checked="rememberme" value="remember-me"> Remember me
        </label>
    </div>
    <button class="btn btn-lg btn-primary btn-block" type="submit" v-on:click="login">Sign in</button>
    <div  v-if="errored" v-html="errormsg" class="alert alert-primary" role="alert"></div>
    <p class="mt-5 mb-3 text-muted">© 2017-2019</p>
    </form>
</div>
</template>
<script>
import { mapState } from 'vuex';
import store from '../store';
import axios from 'axios';

export default {
    name: 'login-screen',
    data: function() {return {
        username: '',
        password: '',
        server: 'https://127.0.0.1:8443/api',
        rememberme: false,
        loading: false,
        errormsg: '',
        errored: false
    }},
    methods: {
        login: function(e) {
            this.errormsg=null;
            this.errored = false;
            console.log(e);
            var credentials = btoa(`${this.username}:${this.password}`);
            var basicAuth = 'Basic ' + credentials;
            this.loading = true;
            axios
            .get(this.server +  '/',{ headers: { 'Authorization': basicAuth }})
            .then(response => {
                console.log("Login Response" ,response.data);
                if(response.data.type == 'OK') {
                  store.dispatch('loginAction',{ user: this.username, pass: this.password , server: this.server });
                }else{
                  this.errormsg=response.data.result;
                }
            })
            .catch(error => {
                console.log(error)
                this.errored = true;
                this.errormsg = error;
            })
            .finally(() => this.loading = false)
            
        }
    }
}
</script>
<style>
#loginscreen {
  text-align: center;
}
.form-signin {
  width: 100%;
  max-width: 330px;
  padding: 15px;
  margin: auto;
}
.form-signin .checkbox {
  font-weight: 400;
}
.form-signin .form-control {
  position: relative;
  box-sizing: border-box;
  height: auto;
  padding: 10px;
  font-size: 16px;
}
.form-signin .form-control:focus {
  z-index: 2;
}
.form-signin input[type="email"] {
  margin-bottom: -1px;
  border-bottom-right-radius: 0;
  border-bottom-left-radius: 0;
}
.form-signin input[type="password"] {
  margin-bottom: 10px;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

</style>
