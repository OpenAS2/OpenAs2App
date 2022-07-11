<template>
  <nav
    class="
      navbar navbar-dark
      fixed-top
      bg-dark
      flex-md-nowrap
      p-0
      d-flex
      shadow
      menu-custom
    "
  >
    <a class="navbar-brand col-sm-3 col-md-2 col-lg-2  mr-0" href="#"> OpenAS2 </a>
    <div class="p-2" @click="openSidemenu">
      <b-icon icon="menu-button-wide" variant="white"></b-icon>
    </div>
    <ul class="navbar-nav px-3 ml-auto">
      <b-nav-item-dropdown block right>
        <template #button-content>
          <b-avatar variant="primary" :text="abbreviationName" size="sm"></b-avatar>
        <small class="ml-2 text-white">{{userName}}</small>
        </template>
        <b-dropdown-item v-on:click="logout" href="#">Sign Out</b-dropdown-item>
      </b-nav-item-dropdown>
    </ul>
  </nav>
</template>
<script>
import store from "../store";
import router from "../routes";

export default {
  name: "top-menu",
  data: function () {
    //console.log(router.options.routes);
    return {
      routes: router.options.routes.filter((e) =>
        e.meta != null ? e.meta.sidemenu : false
      ),
    };
  },
  methods: {
    logout: function (e) {
      store.dispatch("logoutAction", {});
      e.preventDefault();
    },
  },
  props: {
    openSidemenu: {
      require: false,
    },
  },
  computed: {
    actualRoute() {
      return this.$router.name;
    },
    userName(){
      return store.state.username;
    },
    abbreviationName(){
      return store.state.username.substr(0, 2).toUpperCase();
    }
  },
};
</script>
<style>
/*
 * Navbar
 */

.navbar-brand {
  padding-top: 0.75rem;
  padding-bottom: 0.75rem;
  font-size: 1rem;
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}

.form-control-dark {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.1);
}

.form-control-dark:focus {
  border-color: transparent;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.25);
}

/* } */
</style>