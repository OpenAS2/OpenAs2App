<template>
  <div>
    <top-menu
      v-if="$screen.width >= 576"
      :openSidemenu="openSidemenu"
      :closedMenuMovil="closedMenuMovil"
      :showMenu="showMenu"
    />
    <top-menu-phone
      v-if="$screen.width < 576"
      :openSidemenu="openSidemenu"
      :closedMenuMovil="closedMenuMovil"
      :showMenu="showMenu"
    />

    <div class="">
      <b-row no-gutters class="mt-n3">
        <b-col sm="3" md="2" lg="2" >
          <side-menu :showMenu="showMenu" />
        </b-col>
        <!-- <div class="w-100"></div> -->
        <b-col
        class="border-left"
          :sm="showMenu ? 9 : 12"
          :md="showMenu ? 10 : 12"
          :lg="showMenu ? 10 : 12"
        >
          <!-- :xl="showMenu ? 11 : 12" -->
          <b-container fluid class="mt-3">
        
          <!-- <main role="main" class="main-principal"> -->
            <router-view></router-view>
          <!-- </main> -->
          </b-container>
          <!-- <main
            role="main"
            class="ml-sm-auto px-4 main-principal"
          > -->
          <!-- :class="
              showMenu == true
                ? 'col-sm-9 col-md-9 col-lg-10'
                : 'col-sm-12 col-md-12 col-lg-12'
            " -->
          <!-- </main> -->
        </b-col>
        <!--  <p> -->
        <!-- use router-link component for navigation. -->
        <!-- specify the link by passing the `to` prop. -->
        <!-- `<router-link>` will be rendered as an `<a>` tag by default -->
        <!--   <router-link to="/foo">Go to Foo</router-link>   -->
        <!--   <router-link to="/bar">Go to Bar</router-link>   -->
        <!-- </p> -->
        <!-- route outlet -->
        <!-- component matched by the route will render here -->
      </b-row>
    </div>
  </div>
</template>
<script>
import TopMenu from "./TopMenu";
import TopMenuPhone from "./TopMenuPhone";

import SideMenu from "./SideMenu";
export default {
  name: "admin-layout",
  data() {
    return {
      showMenu: true,
    };
  },
  components: {
    TopMenu,
    SideMenu,
    TopMenuPhone,
  },
  methods: {
    closedMenuMovil() {
      this.showMenu = !this.showMenu;
    },
    openSidemenu(e) {
      e.preventDefault();
      this.showMenu = !this.showMenu;
    },
  },
};
</script>
<style scoped>
</style>