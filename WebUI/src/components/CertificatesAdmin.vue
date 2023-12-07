<template>
  <div id="certificates">
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
      <h1 class="h2">Certificates</h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <div class="btn-group mr-2">
          <b-button
            v-if="isPathPartnerList"
            @click="newObject()"
            variant="success"
            size="sm"
            >New</b-button
          >
          <!-- <button type="button" class="btn btn-sm btn-outline-secondary">Export</button> -->
        </div>
      </div>
    </div>
    <template v-if="$route.params.id">
      <certificate-editor
        v-if="$route.params.id"
        title="Certificate Editor"
        :schema="schema"
        :model="getObject($route.params.id)"
        @commit="saveObject"
        @delete="(id) => this.deleteObject(id)"
        @revert="resetModal"
      >
      </certificate-editor>
    </template>

    <template v-else>
      <table-custom
        :loading="loadingTable"
        :items="items"
        :fields="fields"
        @deleteObject="deleteObject"
        @editObject="editObject"
      ></table-custom>
      <b-modal
        hide-footer
        :id="infoModal.id"
        :title="infoModal.title"
        ok-only
        @hidden="resetModal"
        no-close-on-esc
        no-close-on-backdrop
      >
        <form-custom
          :loading="loadingForm"
          v-if="infoModal.title"
          title="Partner Editor"
          :schema="schema"
          :model="item"
          @commit="saveObject"
          @revert="resetModal"
          @fileUpload="fileUpload"
        >
        </form-custom>
      </b-modal>
    </template>
  </div>
</template>
<script>
import FormCustom from "./FormCustom";
import TableCustom from "./TableCustom";
import CertificateEditor from "./CertificateEditor";
import Utils from "../utils";
import Swal from "sweetalert2";
var _ = require("lodash");
export default {
  data: function () {
    return {
      breadcrumbs: [
        {
          text: "Dashboard",
          to: { name: "Dashboard" },
          active: false,
        },
        {
          text: "Certificates",
          to: { name: "Certificates" },
          active: true,
        },
      ],
      schema: {
        fields: [
          {
            type: "text",
            name: "alias",
            label: "Alias Name",
            placeholder: "Certificate's Alias Name",
            _validate: {
              required: true,
            },
          },
          // {
          //   type: "textarea",
          //   name: "data",
          //   label: "PEM Data",
          //   placeholder: "Trading Partner's AS2 ID",
          //   _validate: {
          //     required: true,
          //   },
          // },
          {
            type: "text",
            name: "serial",
            label: "Serial Number (HEX)",
            placeholder: "Trading Partner's Email Address",
            show: true,
            disabled: true,
            _validate: {
              email: true,
            },
          },
          {
            type: "datepicker",
            name: "validNotBefore",
            label: "Valid Not Before",
            placeholder: "Valid Not Before",
            show: true,
            disabled: true,
            _validate: {},
          },
          {
            type: "datepicker",
            name: "validNotAfter",
            label: "Expiration Date",
            placeholder: "Expiration Date",
            show: true,

            disabled: true,
            _validate: {},
          },
          {
            type: "text",
            name: "subject",
            label: "Subject",
            placeholder: "Subject",
            disabled: true,
            show: true,
            _validate: {},
          },
          {
            type: "text",
            name: "fingerPrint",
            label: "Fingerprint (SHA1)",
            placeholder: "Fingerprint (SHA1)",
            show: true,
            disabled: true,
            _validate: {},
          },
          {
            type: "file",
            accept: ".pem",
            name: "upload",
            label: "Upload Certificate",
            placeholder: "Upload Certificate",
            _validate: {
              required: true,
              ext: "pem",
            },
          },
        ],
      },
      fields: [
        {
          key: "key",
          label: "#",
          sortable: true,
          class: "text-center",
        },
        {
          key: "name",
          label: "Certificate ID",
          sortable: true,
          class: "text-center",
        },
        { key: "actions", label: "Actions", class: "text-center" },
      ],
      infoModal: {
        id: "info-modal",
        title: "",
      },
      items: [],
      item: {
        _id: null,
        alias: "",
        data: "",
        serial: "",
        issuerNam: "",
        validNotBefore: "",
        validNotAfter: "",
        subject: "",
        fingerPrint: "",
      },
      loadingForm: false,
      loadingTable: false,
    };
  },
  components: {
    FormCustom,
    TableCustom,
    CertificateEditor,
  },
  computed: {
    isPathPartnerList() {
      return this.$route.name == "Certificates";
    },
  },
  mounted: async function () {
    var list = await Utils.Crud.getList("cert");
    this.schema.fields[3].list = _.map(list, (item) => {
      return { value: item, text: item };
    });
    await this.getList();
  },
  methods: {
    getObject: async function (index) {
      var newObj = this.getNewObject();
      var obj = await Utils.Crud.getObject("cert", index, newObj);
      return obj;
    },
    resetModal: function () {
      this.infoModal.title = "";
      this.item = {
        _id: null,
        alias: "",
        data: "",
        serial: "",
        issuerNam: "",
        validNotBefore: "",
        validNotAfter: "",
        subject: "",
        fingerPrint: "",
      };
      this.$root.$emit("bv::hide::modal", this.infoModal.id);
      this.loadingForm = this.loadingTable = false;
    },
    async existCertificate(cert) {
      let exist = false;
      await Utils.Crud.getObjectFilter("partner", cert.key, cert)
        .then((value) => {
          exist = true;
        })
        .catch(() => {
          exist = false;
        });
      return exist;
    },
    deleteObject: async function (item) {
      try {
        this.loadingTable = true;
        Swal.fire({
          title: "Are you sure to delete?",
          html: `a cert  ${item.name}`,
          icon: "warning",
          showCancelButton: true,
          confirmButtonText: "Yes",
          cancelButtonText: "No",
        }).then(async (result) => {
          if (result.isConfirmed) {
            let exist = await this.existCertificate(item);
            if (!exist) {
              Utils.Crud.deleteObject("cert", item.key)
                .then((response) => {
                  Swal.fire("Deleted!", "", "success");
                  this.getList();
                })
                .catch((e) => {
                  console.log("delete parner", e);
                  Swal.fire("Error!", e, "error");
                });
            } else {
              Swal.fire(
                "Warning!",
                "The certificate is registered with a partner",
                "warning"
              );
            }
          }
          this.loadingTable = false;
        });
      } catch (e) {
        console.log(e);
        Swal.fire("Error!", e, "error");
      }
    },
    newObject: function () {
      this.infoModal.title = "New certificate";
      this.item = {
        _id: null,
        alias: "",
        data: "",
        serial: "",
        issuerNam: "",
        validNotBefore: "",
        validNotAfter: "",
        subject: "",
        fingerPrint: "",
      };
      this.schema.fields[this.schema.fields.length - 1]._validate = true;
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    editObject: async function (_item) {
      console.log("item_edit", _item);
      this.infoModal.title = "Edit certificate";
      var newObj = this.getNewObject();
      const result = await Utils.Crud.getObject("cert", _item.key, newObj);
      console.log("resutl al editar", result);
      this.item = Object.assign(this.item, result);
      this.changedCert();
      this.schema.fields[this.schema.fields.length - 1]._validate = false;
      this.$root.$emit("bv::show::modal", this.infoModal.id);
    },
    getNewObject: function () {
      this.loadingTable = true;
      var obj = { _id: null };
      this.schema.fields.forEach((f) => (obj[f.name] = f.value));
      return obj;
    },
    getList: async function () {
      this.items = await Utils.Crud.getList("cert").then((results) => {
        return _.map(results, (item, index) => {
          return {
            key: index,
            name: item,
            actions: {
              edit: { name: "Edit", show: true },
              delete: { name: "Delete", show: true },
            },
          };
        });
      });
    },
    saveObject: async function (data) {
      this.loadingForm = this.loadingTable = true;
      try {
        let _data = {
          _id: null,
          alias: data.alias,
          data: data.data,
        };
        console.log("Saving", data);
        var old_id = data._id;
        // delete data._id;
        console.log("old_id", old_id);
        if (old_id !== null) {
          await Utils.Crud.deleteObject("cert", old_id).then(
            async () =>
              // this.importCertificate(data)
              await Utils.Crud.importCertificate(_data)
          );
        } else {
          await Utils.Crud.importCertificate(_data);
        }
        // await Utils.Crud.saveObject("cert", data);
        this.resetModal();
        await this.getList();
        this.loadingForm = this.loadingTable = false;
      } catch (e) {
        Swal.fire("Error!", e, "error");
        this.loadingForm = false;
      }
    },
    fileUpload: function ({ file, e }) {
      console.log("item.....", this.item);
      var oFile = file;
      console.log(file);
       // filter for certificate files
       var rFilter = /\.(pem)$/i;
       var fileName = oFile.name;
       if (!rFilter.test(fileName)) {
        alert("Error invalid file-type");
        return;
      }
      if (oFile.size == 0) {
        alert("Error unable to read file, empty?");
        return;
      }
      var oReader = new FileReader();
      var that = this;
      oReader.onload = function (e) {
        console.log("edit---->", e.target.result);
        var PEM = e.target.result;
        try {
           PEM = PEM.replace("-----BEGIN CERTIFICATE-----", "").replace(
            "-----END CERTIFICATE-----","");
            PEM = PEM.replace(/[^A-Za-z0-9+/=]+/gm, "");
            that.item.data = PEM;
            that.changedCert();
            console.log("Read certificate successfully")
        } catch (ex) {
            console.log("Error reading certificate", ex)
            alert("Error reading certificate");
            return;
        }
      };
      oReader.readAsText(oFile);
    },
    changedCert: function () {
      var cert = new X509();
      console.log("this.item.data", this.item.data);
      var PEM = this.item.data;
      console.log("item.....", this.item);
      if (this.item.data == null) {
        this.item.serial = "";
        this.item.issuerName = "";
        this.item.validNotBefore = null;
        this.item.validNotAfter = null;
        this.item.subject = "";
        this.item.fingerPrint = "";
        return;
      }
      if (PEM.substr(0, 4) !== "----") {
        PEM =
          "-----BEGIN CERTIFICATE-----\n" + PEM + "\n-----END CERTIFICATE-----";
      }

      console.log("PEM changedCert", PEM);
      cert.readCertPEM(PEM);
      this.item.serial = cert.getSerialNumberHex();
      this.item.issuerName = cert.getIssuerString();
      this.item.validNotBefore = zulutodate(cert.getNotBefore());
      this.item.validNotAfter = zulutodate(cert.getNotAfter());
      this.item.subject = cert.getSubjectString();
      this.item.fingerPrint = KJUR.crypto.Util.hashHex(cert.hex, "sha1");
      console.log("item changedCert", this.item);
      console.log("cert changedCert", cert);
    },
  },
};
</script>
<style >
</style>