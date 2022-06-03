<template>
  <div id="dashboard">
    <div
      class="
        d-flex
        justify-content-between
        flex-wrap flex-md-nowrap
        align-items-center
        pt-3
        pb-2
        mb-3
        border-bottom
      "
    >
      <h1 class="h2">Dashboard</h1>
    </div>

    <b-row>
      <!-- <b-card-group deck> -->
      <b-col md="4">
        <b-card
          bg-variant="warning"
          text-variant="white"
          header="Total Partners"
          class="text-center mt-1"
        >
          <b-card-text
            ><h1>{{ countPartners }}</h1></b-card-text
          >
        </b-card>
      </b-col>
      <b-col md="4">
        <b-card
          bg-variant="dark"
          text-variant="white"
          header="Total Connections"
          class="text-center mt-1"
        >
          <b-card-text>
            <h1>{{ countCertificates }}</h1>
          </b-card-text>
        </b-card>
      </b-col>
      <b-col md="4">
        <b-card
          bg-variant="info"
          text-variant="white"
          header="Total Certificates"
          class="text-center mt-1"
        >
          <b-card-text
            ><h1>{{ countPartnerships }}</h1></b-card-text
          >
        </b-card>
      </b-col>
      <!-- </b-card-group> -->
      <b-row class="justify-content-md-center mt-3">
        <b-col md="4">
          <date-range-picker
            @update="changeDate"
            v-model="dateRange"
            opens="left"
          ></date-range-picker>
          <!-- :locale="locale" -->
        </b-col>
      </b-row>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-line-two
          :labels="dates"
          :messagesSent="messagesSent"
          :messagesReceived="messagesReceived"
          :messagesFailed="messagesFailed"
        >
        </chart-line-two>
      </b-col>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-line
          :labels="dates"
          :messagesSent="messagesSent"
          :messagesReceived="messagesReceived"
          :messagesFailed="messagesFailed"
        >
        </chart-line>
      </b-col>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-bar
          :labels="dates"
          :datasets="datasetsMessagesSent"
          :title="'Sent messages '"
        >
        </chart-bar>
      </b-col>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-bar
          :labels="dates"
          :datasets="datasetsMessagesReceived"
          :title="'Received messages'"
        >
        </chart-bar>
      </b-col>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-bar
          :labels="dates"
          :datasets="datasetsMessagesFailed"
          :title="'Failed messages'"
        >
        </chart-bar>
      </b-col>
      <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-bar
          :labels="dates"
          :datasets="datasets"
          :title="'Failed messages'"
        >
        </chart-bar>
      </b-col>
      <messages-admin></messages-admin>
    </b-row>
  </div>
</template>
<script>
import ChartLine from "./ChartLine.vue";
import MessagesAdmin from "./MessagesAdmin.vue";

import ChartLineTwo from "./ChartLineTwo.vue";
import ChartBar from "./ChartBar";

import Utils from "../utils";
import DateRangePicker from "vue2-daterange-picker";
//you need to import the CSS manually
import "vue2-daterange-picker/dist/vue2-daterange-picker.css";

import moment from "moment";
var XLSX = require("xlsx");
export default {
  name: "dashboard",
  components: {
    ChartLine,
    MessagesAdmin,
    ChartLineTwo,
    ChartBar,
    DateRangePicker,
  },
  data() {
    return {
      length: 10,
      messagesSent: [],
      messagesReceived: [],
      messagesFailed: [],
      datasetsMessagesSent: [],
      datasetsMessagesReceived: [],
      datasetsMessagesFailed: [],
      datasets: [],
      dates: [],
      partners: [],
      certificates: [],
      partnerships: [],
      dateRange: {
        startDate: moment().startOf("week"),
        endDate: moment(),
      },
      
      // locale: {
      //   direction: "ltr",
      //   format: "DD-MM-YYYY",
      //   separator: " - ",
      //   applyLabel: "Apply",
      //   cancelLabel: "Cancel",
      //   weekLabel: "W",
      //   customRangeLabel: "Custom Range",
      //   daysOfWeek: moment.weekdaysMin(),
      //   monthNames: moment.monthsShort(),
      //   firstDay: 1,
      // },
    };
  },
  computed: {
    countPartners: function () {
      return this.partners.length;
    },
    countCertificates: function () {
      return this.certificates.length;
    },
    countPartnerships: function () {
      return this.partnerships.length;
    },
  },
  mounted() {
    this.generateDates();
    this.getListMessageSent();
    this.getListMessageReceived();
    this.getListMessageFailed();
    this.getListPartners();
    this.getListCertificates();
    this.getListConnections();
  },
  methods: {
    dateFormat(classes, date) {
      if (!classes.disabled) {
        classes.disabled = date.getTime() < new Date();
      }
      return classes;
    },
    getListPartners: async function () {
      this.partners = await Utils.Crud.getList("partner");
    },
    getListCertificates: async function () {
      this.certificates = await Utils.Crud.getList("cert");
    },
    getListConnections: async function () {
      this.partnerships = await Utils.Crud.getList("partnership");
    },
    getListMessageSent: function () {
      this.messagesSent = [];

      let min = 1;
      let max = 100;
      let init = moment(this.dateRange.startDate);
      let now = moment();
      let end = moment(this.dateRange.endDate).isSameOrBefore(now)
        ? moment(this.dateRange.endDate)
        : moment(now);
      let tam = end.diff(init, "days");
      for (let index = 0; index <= tam; index++) {
        this.messagesSent.push(Math.round(Math.random() * (max - min) + min));
      }
      this.datasetsMessagesSent.push({
        label: "Sent messages",
        backgroundColor: "#28a745",
        data: this.messagesSent,
      });
      this.datasets.push({
        label: "Sent messages",
        backgroundColor: "#28a745",
        data: this.messagesSent,
      });
    },
    getListMessageReceived: function () {
      this.messagesReceived = [];
      let min = 1;
      let max = 100;
      let init = moment(this.dateRange.startDate);
      let now = moment();
      let end = moment(this.dateRange.endDate).isSameOrBefore(now)
        ? moment(this.dateRange.endDate)
        : moment(now);
      let tam = end.diff(init, "days");
      for (let index = 0; index <= tam; index++) {
        this.messagesReceived.push(
          Math.round(Math.random() * (max - min) + min)
        );
      }
      this.datasetsMessagesReceived.push({
        label: "Received messages",
        backgroundColor: "#007bff",
        data: this.messagesReceived,
      });
      this.datasets.push({
        label: "Received messages",
        backgroundColor: "#007bff",
        data: this.messagesReceived,
      });
    },
    getListMessageFailed: function () {
      this.messagesFailed = [];
      let min = 1;
      let max = 100;
      // let tam=moment.duration(moment(this.dateRange.endDate).diff(moment(this.dateRange.startDate)))
      let init = moment(this.dateRange.startDate);
      let now = moment();
      let end = moment(this.dateRange.endDate).isSameOrBefore(now)
        ? moment(this.dateRange.endDate)
        : moment(now);
      let tam = end.diff(init, "days");
      for (let index = 0; index <= tam; index++) {
        this.messagesFailed.push(Math.round(Math.random() * (max - min) + min));
      }
      this.datasetsMessagesFailed.push({
        label: "Failed messages",
        backgroundColor: "#FF0000",
        data: this.messagesFailed,
      });
      this.datasets.push({
        label: "Failed messages",
        backgroundColor: "#FF0000",
        data: this.messagesFailed,
      });
    },
    generateDates: function () {
      this.dates = [];
      let init = moment(this.dateRange.startDate).format("YYYY-MM-DD");
      let end = moment(this.dateRange.endDate).format("YYYY-MM-DD");
      let now = moment().format("YYYY-MM-DD");

      let end2 = moment(this.dateRange.endDate).isSameOrBefore(now)
        ? moment(this.dateRange.endDate)
        : moment(now);
      this.length = end2.diff(init, "days");

      while (
        moment(init).isSameOrBefore(end) &&
        moment(init).isSameOrBefore(now)
      ) {
        this.dates.push(init);
        init = moment(init).add(1, "days").format("YYYY-MM-DD");
      }
    },
    changeDate: function () {
      this.datasets = [];
      this.datasetsMessagesSent = [];
      this.datasetsMessagesReceived = [];
      this.datasetsMessagesFailed = [];
      this.getListMessageSent();
      this.getListMessageReceived();
      this.getListMessageFailed();
      this.generateDates();
    },
  },
};
</script>
<style scoped>
/*
 * Content
 */

[role="main"] {
  padding-top: 133px; /* Space for fixed navbar */
}

@media (min-width: 768px) {
  [role="main"] {
    padding-top: 48px; /* Space for fixed navbar */
  }
}
</style>