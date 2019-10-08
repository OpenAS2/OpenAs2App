import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)


const store = new Vuex.Store({
    debug: true,
    state: {
        username: null,
        password: null,
        server: null
    },
    actions: {
        loginAction(context,payload) {
            if(this.debug) console.log(`Logging with ${payload.user} and ${payload.pass}`);
            context.commit('login',payload);
        },
        logoutAction(context ) {
            if(this.debug) console.log(`Logout`);
            context.commit('logout');
        }
    },
    mutations: {
        login (state,payload) {
            state.username=payload.user;
            state.password=payload.pass;
            state.server=payload.server;
            return state;
        },
        logout(state) {
            state.username=null;
            state.password=null;
            return state;
        }
    }
    
});

export default store;