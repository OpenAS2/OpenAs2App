<template>
  <div id="dashboard">
    <b-breadcrumb :items="breadcrumbs"></b-breadcrumb>

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
            > <b-row align-h="between">
              <b-col> <b-icon icon="person" font-scale="3 "></b-icon></b-col>
              <b-col>
                <h1>{{ countPartners }}</h1></b-col
              >
            </b-row></b-card-text
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
            <b-row align-h="between">
              <b-col> <b-icon icon="people" font-scale="3 "></b-icon></b-col>
              <b-col>
                <h1>{{ countPartnerships }}</h1></b-col
              >
            </b-row>
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
            >
            <b-row align-h="between">
              <b-col> <b-icon icon="file-earmark-richtext" font-scale="3 "></b-icon></b-col>
              <b-col>
                <h1>{{countCertificates  }}</h1></b-col
              >
            </b-row>
            </b-card-text
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
        </b-col>
      </b-row>
      <!-- <b-col class="mt-3" :md="length < 15 ? '6' : '12'">
        <chart-line-two
          :labels="dates"
          :messagesSent="messagesSent"
          :messagesReceived="messagesReceived"
          :messagesFailed="messagesFailed"
        >
        </chart-line-two>
      </b-col> -->
      <b-col class="mt-3" :md="dataChart['sent'].valuesX.length < 15 ? '6' : '12'">
        <chart-line
          :labels="dataChart['sent'].valuesX"
          :datasets="allState"
        >
        </chart-line>
      </b-col>
      <b-col
        v-for="(state, index) in states"
        :key="index"
        class="mt-3"
        :md="dataChart[state].valuesX.length < 15 ? '6' : '12'"
      >
        <chart-bar
          :labels="dataChart[state].valuesX"
          :datasets="dataChart[state].valuesY"
          :title="dataChart[state].valuesX.label"
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

// import ChartLineTwo from "./ChartLineTwo.vue";
import ChartBar from "./ChartBar";

import Utils from "../utils";
import DateRangePicker from "vue2-daterange-picker";
//you need to import the CSS manually
import "vue2-daterange-picker/dist/vue2-daterange-picker.css";
var _ = require("lodash");
import moment from "moment";
var XLSX = require("xlsx");
export default {
  name: "dashboard",
  components: {
    ChartLine,
    MessagesAdmin,
    // ChartLineTwo,
    ChartBar,
    DateRangePicker,
  },
  data() {
    return {
      breadcrumbs: [
        {
          text: "Dashboard",
          to: { name: "Dashboard" },
          active: true,

        },
      ],
      length: 10,
      messages: {
        sent: [],
        receive: [],
        fail: [],
      },
      dataChartAll: {
        valuesX: [],
        valuesY: [],
      },
      dataChart: {
        sent: {
          valuesX: [],
          valuesY: [
            {
              label: "Sent messages",
              backgroundColor: "#28a745",
              borderColor: "#28a745",
              data: [],
            },
          ],
        },
        receive: {
          valuesX: [],
          valuesY: [
            {
              label: "Received messages",
              backgroundColor: "#007bff",
              borderColor: "#007bff",
              data: [],
            },
          ],
        },
        fail: {
          valuesX: [],
          valuesY: [
            {
              label: "Failed messages",
              backgroundColor: "#FF0000",
              borderColor: "#FF0000",
              data: [],
            },
          ],
        },
      },
      partners: [],
      certificates: [],
      partnerships: [],
      dateRange: {
        startDate: moment().startOf("week"),
        endDate: moment(),
      },
      states: ["sent", "receive", "fail"],
      stateMap: {
        sent: [
          "msg_send_start",
          "mdn_send_start",
          "msg_rxd_mdn_sent_ok",
          "msg_rxd_mdn_not_requested_ok",
        ],
        receive: [
          "msg_sent_mdn_received_mic_mismatch",
          "msg_sent_mdn_received_ok",
          "msg_receive_start",
          "mdn_receive_start",
        ],
        fail: [
          "msg_send_exception",
          "msg_receive_exception",
          "mdn_sending_exception",
          "mdn_receiving_exception",
          "msg_send_fail",
          "msg_send_fail_resend_queued",
          "msg_receive_fail",
          "msg_rxd_asyn_mdn_send_fail_resend_queued",
          "mdn_asyn_receive_fail",
          "msg_rxd_mdn_sending_fail",
          "msg_receive_error_sending_mdn_error",
          "msg_sent_mdn_received_error",
        ],
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
    allState() {
      let list = [];
      this.states.forEach((state) => {
        list.push(this.dataChart[state].valuesY[0]);
      });
      return list;
    },
  },
  mounted() {
    this.getListPartners();
    this.getListCertificates();
    this.getListConnections();
    this.getListMessageChart();
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
    getListMessageChart: async function () {
      const data = {
        startDate: moment(this.dateRange.startDate).format("YYYY-MM-DD"),
        endDate: moment(this.dateRange.endDate).format("YYYY-MM-DD"),
      };
      let messages = await Utils.Crud.getListChart("messages", data);
      messages.forEach((message) => {
        this.states.forEach((state) => {
          if (this.stateMap[state].includes(message.STATE)) {
            this.messages[state].push({
              date: moment(message.CREATE_DT).format("YYYY-MM-DD"),
              name: message.MSG_ID,
            });
          }
        });
      });
      let ini = moment(data.startDate);
      let end = moment(data.endDate);
      while (ini.isBefore(end)) {
        let inicio = ini.format("YYYY-MM-DD");
        this.states.forEach((state) => {
          this.dataChart[state].valuesX.push(inicio);
          let filter = _.filter(this.messages[state], (msg) => {
            return moment(msg.date).format("YYYY-MM-DD") == inicio;
          });
          let count = filter.length > 0 ? filter.length : 0;
          this.dataChart[state].valuesY[0].data.push(count);
        });
        ini.add(1, "days");
      }
    },
   
    
    changeDate: function () {
      this.getListMessageChart();
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

.v-text-field {
  padding: 0;
}

.fixWidth {
  max-width: 180px;
}

.selectableWidth {
  max-width: 200px;
}

@media (min-width: 768px) {
  [role="main"] {
    padding-top: 48px; /* Space for fixed navbar */
  }
}
</style>